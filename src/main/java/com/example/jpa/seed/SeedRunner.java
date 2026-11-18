package com.example.jpa.seed;

import com.example.jpa.common.Address;
import com.example.jpa.common.Money;
import com.example.jpa.member.*;
import com.example.jpa.menu.Menu;
import com.example.jpa.menu.MenuGroup;
import com.example.jpa.menu.OptionGroup;
import com.example.jpa.store.Category;
import com.example.jpa.store.Store;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

// 시드 적재. seed 프로파일에서만 빈으로 등록된다 → 평소 기동/테스트에는 영향 없음
//   실행: ./gradlew bootRun --args='--spring.profiles.active=local,seed'
//   다시 넣기: 앱은 ddl-auto: validate 라 테이블을 지우지 않는다. 데이터가 있으면 run() 이 막는다.
//     중간에 실패해 반쯤 들어갔거나 처음부터 다시 넣고 싶으면 그때만 create 로 덮어써서 실행한다
//     ./gradlew bootRun --args='--spring.profiles.active=local,seed --spring.jpa.hibernate.ddl-auto=create'
//     (청크마다 커밋하므로 실패 시점까지의 데이터는 남는다)
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {

    private static final int CUSTOMERS = 10_000;
    private static final int OWNERS = 300;
    private static final int RIDERS = 200;
    private static final int STORES = 300;
    private static final int CHUNK = 500;          // 이만큼 persist 할 때마다 flush + clear
    private static final int STORE_CHUNK = 20;     // 가게는 자식이 많아서 작게

    private final EntityManager em;
    private final TransactionTemplate tx;
    private final Random random = new Random(42); // 고정 시드: 몇 번을 돌려도 같은 데이터

    @Override
    public void run(ApplicationArguments args) {
        Long members = tx.execute(s -> em.createQuery("select count(m) from Member m", Long.class).getSingleResult());
        if (members != null && members > 0) {
            throw new IllegalStateException("이미 데이터가 있습니다(member " + members + "건). 비운 뒤 실행하세요.");
        }
        long start = System.currentTimeMillis();

        seedCustomers();
        List<Long> ownerIds = seedOwners();
        seedRiders();
        List<Long> leafCategoryIds = seedCategories();
        seedStores(ownerIds, leafCategoryIds);

        log.info("시드 완료: {}ms", System.currentTimeMillis() - start);
        printCounts();
    }

    // ───────────────────────── 회원 ─────────────────────────

    private void seedCustomers() {
        for (int from = 0; from < CUSTOMERS; from += CHUNK) {
            int start = from;
            tx.executeWithoutResult(s -> {
                for (int i = start; i < Math.min(start + CHUNK, CUSTOMERS); i++) {
                    Customer customer = Customer.of("customer" + i + "@seed.com", "1234", randomName(), phone(1, i));
                    int addressCount = 1 + random.nextInt(3);            // 1~3개. 첫 주소가 기본 주소가 된다
                    for (int a = 0; a < addressCount; a++) {
                        customer.addAddress(CustomerAddress.of(ADDRESS_ALIASES[a], randomAddress(), randomLat(), randomLng()));
                    }
                    customer.changeGrade(randomGrade());
                    if (random.nextInt(100) < 2) customer.withdraw();   // 2% 탈퇴
                    em.persist(customer);
                }
                em.flush();
                em.clear();
            });
            log.info("customer {}/{}", Math.min(from + CHUNK, CUSTOMERS), CUSTOMERS);
        }
    }

    private List<Long> seedOwners() {
        List<Owner> owners = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < OWNERS; i++) {
                Owner owner = Owner.of("owner" + i + "@seed.com", "1234", randomName(), phone(2, i),
                        String.format("100-00-%05d", i));
                em.persist(owner);
                owners.add(owner);
            }
        });
        return owners.stream().map(Owner::getId).toList();   // 가게 적재 때 getReference 로 다시 쓴다
    }

    private void seedRiders() {
        VehicleType[] types = VehicleType.values();
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < RIDERS; i++) {
                em.persist(Rider.of("rider" + i + "@seed.com", "1234", randomName(), phone(3, i),
                        types[random.nextInt(types.length)]));
            }
        });
    }

    // ───────────────────────── 카테고리 ─────────────────────────

    // 루트 → 중분류 → 소분류. 소분류가 없는 중분류는 그 자체가 말단(가게가 붙는 곳)
    private static final Map<String, Map<String, List<String>>> CATEGORY_TREE = new LinkedHashMap<>();
    static {
        CATEGORY_TREE.put("한식", linked("찌개·탕", List.of("김치찌개", "된장찌개", "감자탕"), "고기", List.of("삼겹살", "갈비"), "국밥", List.of("순대국", "돼지국밥")));
        CATEGORY_TREE.put("중식", linked("면", List.of("짜장면", "짬뽕"), "요리", List.of("탕수육", "마라")));
        CATEGORY_TREE.put("일식", linked("초밥·회", List.of("초밥", "회"), "면", List.of("라멘", "우동"), "돈가스", List.of()));
        CATEGORY_TREE.put("양식", linked("피자", List.of(), "파스타", List.of(), "버거", List.of()));
        CATEGORY_TREE.put("분식", linked("떡볶이", List.of(), "김밥", List.of(), "튀김", List.of()));
        CATEGORY_TREE.put("카페", linked("커피", List.of(), "디저트", List.of("케이크", "마카롱"), "음료", List.of()));
    }

    private List<Long> seedCategories() {
        List<Category> leaves = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            int rootOrder = 1;
            for (var root : CATEGORY_TREE.entrySet()) {
                Category r = Category.root(root.getKey(), rootOrder++);
                int midOrder = 1;
                for (var mid : root.getValue().entrySet()) {
                    Category m = r.addChild(mid.getKey(), midOrder++);
                    if (mid.getValue().isEmpty()) {
                        leaves.add(m);
                    }
                    int leafOrder = 1;
                    for (String leaf : mid.getValue()) {
                        leaves.add(m.addChild(leaf, leafOrder++));
                    }
                }
                em.persist(r);     // children 에 cascade PERSIST → 트리 전체 INSERT (IDENTITY 라 id 즉시 생김)
                r.assignPath();    // id 가 생긴 뒤라 path 를 만들 수 있다 → flush 때 UPDATE
            }
        });
        return leaves.stream().map(Category::getId).toList();
    }

    // ───────────────────────── 가게 · 메뉴 · 옵션 ─────────────────────────

    private void seedStores(List<Long> ownerIds, List<Long> leafCategoryIds) {
        for (int from = 0; from < STORES; from += STORE_CHUNK) {
            int start = from;
            tx.executeWithoutResult(s -> {
                List<Long> menusToDelete = new ArrayList<>();
                for (int i = start; i < Math.min(start + STORE_CHUNK, STORES); i++) {
                    menusToDelete.addAll(seedStore(i, ownerIds, leafCategoryIds));
                }
                em.flush();
                em.clear();
                // 소프트 삭제는 clear 뒤에 한다. menuGroup.menus 가 영속성 컨텍스트에 남아 있으면
                // flush 때 cascade PERSIST 가 삭제를 취소한다(Step 3-8 에서 본 "삭제 예약 → 취소")
                menusToDelete.forEach(id -> em.remove(em.find(Menu.class, id)));
                em.flush();
                em.clear();
            });
            log.info("store {}/{}", Math.min(from + STORE_CHUNK, STORES), STORES);
        }
    }

    // 소프트 삭제할 메뉴 id 를 돌려준다
    private List<Long> seedStore(int i, List<Long> ownerIds, List<Long> leafCategoryIds) {
        // 점주 300명 중 앞 285명이 1개씩, 마지막 15개 가게는 앞쪽 점주가 2번째 가게를 갖는다 → 점주 15명은 가게 없음
        Long ownerId = (i < 285) ? ownerIds.get(i) : ownerIds.get(i - 285);
        Owner owner = em.getReference(Owner.class, ownerId);   // SELECT 없이 FK 값만 채운다

        Category primary = em.getReference(Category.class, leafCategoryIds.get(random.nextInt(leafCategoryIds.size())));
        String district = pick(DISTRICTS);
        Store store = Store.of(owner, primary.getName() + " " + pick(STORE_SUFFIXES) + " " + district + "점",
                randomAddress(district),
                Money.of(10_000 + random.nextInt(11) * 1_000), Money.of(random.nextInt(9) * 500),
                20 + random.nextInt(9) * 5);

        store.addCategory(primary, true);
        if (random.nextInt(100) < 60) {                             // 60% 는 카테고리 2개
            Long secondId = leafCategoryIds.get(random.nextInt(leafCategoryIds.size()));
            if (!secondId.equals(primary.getId())) {
                store.addCategory(em.getReference(Category.class, secondId), false);
            }
        }
        seedBusinessHours(store);
        if (random.nextBoolean()) {                                 // 절반은 휴무일 1~2개
            LocalDate base = LocalDate.of(2026, 12, 1);
            Set<Integer> days = new HashSet<>();
            int count = 1 + random.nextInt(2);
            while (days.size() < count) days.add(random.nextInt(31));
            days.forEach(d -> store.addHoliday(base.plusDays(d), random.nextBoolean() ? "개인 사정" : null));
        }

        String root = rootOf(primary);
        List<Menu> menus = seedMenus(store, root);

        int status = random.nextInt(100);
        if (status < 5) store.pause();                              // 5% 임시 중지
        else if (status < 8) store.close();                         // 3% 폐업

        em.persist(store);                                          // cascade: 카테고리 연결, 영업시간, 휴무일, 메뉴그룹 → 메뉴

        List<OptionGroup> optionGroups = seedOptionGroups(store);   // store_id 가 필요해서 가게 다음에 저장
        for (Menu menu : menus) {
            linkOptionGroups(menu, optionGroups);
        }
        List<Long> toDelete = new ArrayList<>();
        for (Menu menu : menus) {
            if (random.nextInt(100) < 2) toDelete.add(menu.getId());   // 2% 소프트 삭제 대상
        }
        return toDelete;
    }

    private void seedBusinessHours(Store store) {
        DayOfWeek closedDay = DayOfWeek.of(1 + random.nextInt(7));  // 하루는 정기휴무(행 없음)
        boolean breakTime = random.nextInt(100) < 30;               // 30% 브레이크타임 → 하루 2구간
        boolean lateNight = random.nextInt(100) < 15;               // 15% 새벽 마감 → close < open
        LocalTime open = LocalTime.of(9 + random.nextInt(3), 0);
        LocalTime close = lateNight ? LocalTime.of(2, 0) : LocalTime.of(21 + random.nextInt(2), 0);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == closedDay) continue;
            if (breakTime) {
                store.addBusinessHour(day, open, LocalTime.of(15, 0));
                store.addBusinessHour(day, LocalTime.of(17, 0), close);
            } else {
                store.addBusinessHour(day, open, close);
            }
        }
    }

    private List<Menu> seedMenus(Store store, String root) {
        List<Menu> menus = new ArrayList<>();
        String[] groupNames = random.nextBoolean()
                ? new String[]{"대표메뉴", "사이드", "음료"}
                : new String[]{"대표메뉴", "세트", "사이드", "음료"};
        for (int g = 0; g < groupNames.length; g++) {
            MenuGroup group = store.addMenuGroup(groupNames[g], g + 1);
            List<String> pool = switch (groupNames[g]) {
                case "사이드" -> SIDE_MENUS;
                case "음료" -> DRINK_MENUS;
                default -> MAIN_MENUS.get(root);
            };
            int count = 3 + random.nextInt(6);                      // 그룹당 3~8개
            for (int m = 0; m < count; m++) {
                String name = pool.get(random.nextInt(pool.size()));
                if (groupNames[g].equals("세트")) name = name + " 세트";
                Menu menu = group.addMenu(name, random.nextInt(100) < 30 ? name + " 설명" : null,
                        Money.of(groupNames[g].equals("음료") ? 1_500 + random.nextInt(6) * 500 : 5_000 + random.nextInt(31) * 500),
                        g == 0 && m == 0, m + 1);
                int status = random.nextInt(100);
                if (status < 5) menu.soldOut();                     // 5% 품절
                else if (status < 8) menu.hide();                   // 3% 숨김
                menus.add(menu);
            }
        }
        return menus;
    }

    private List<OptionGroup> seedOptionGroups(Store store) {
        List<OptionTemplate> templates = new ArrayList<>(OPTION_TEMPLATES);
        Collections.shuffle(templates, random);
        int count = 3 + random.nextInt(3);                          // 가게당 3~5개
        List<OptionGroup> groups = new ArrayList<>();
        for (OptionTemplate t : templates.subList(0, count)) {
            OptionGroup group = OptionGroup.of(store, t.name(), t.min(), t.max());
            for (int k = 0; k < t.items().size(); k++) {
                group.addItem(t.items().get(k), Money.of(t.prices().get(k)), k + 1);
            }
            em.persist(group);                                      // addOptionGroup 은 id 가 있어야 연결된다
            groups.add(group);
        }
        return groups;
    }

    private void linkOptionGroups(Menu menu, List<OptionGroup> optionGroups) {
        int r = random.nextInt(100);
        int count = r < 60 ? 1 : (r < 90 ? 2 : 3);                 // 메뉴당 1~3개 (평균 1.5)
        List<OptionGroup> shuffled = new ArrayList<>(optionGroups);
        Collections.shuffle(shuffled, random);
        for (int k = 0; k < Math.min(count, shuffled.size()); k++) {
            menu.addOptionGroup(shuffled.get(k), k + 1);
        }
    }

    // ───────────────────────── 확인 ─────────────────────────

    private void printCounts() {
        tx.executeWithoutResult(s -> {
            for (String entity : List.of("Category", "Member", "Customer", "Owner", "Rider", "CustomerAddress",
                    "Store", "StoreCategory", "StoreBusinessHour", "StoreHoliday",
                    "MenuGroup", "Menu", "OptionGroup", "OptionItem", "MenuOptionGroup")) {
                Long count = em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
                log.info("{} = {}", entity, count);
            }
            Number allMenus = (Number) em.createNativeQuery("select count(*) from menu").getSingleResult();
            log.info("menu(삭제 포함, 네이티브) = {}", allMenus);
        });
    }

    // ───────────────────────── 랜덤 재료 ─────────────────────────

    private static final String[] LAST_NAMES = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권"};
    private static final String[] FIRST_NAMES = {"민준", "서연", "도윤", "하은", "시우", "지유", "주원", "서윤", "예준", "지민", "하준", "수아", "지호", "채원", "현우"};
    private static final String[] DISTRICTS = {"강남", "역삼", "삼성", "잠실", "송파", "서초", "마포", "합정", "홍대", "신촌", "종로", "을지로", "성수", "건대", "영등포"};
    private static final String[] STREETS = {"테헤란로", "강남대로", "올림픽로", "양화로", "종로", "을지로", "성수이로", "영중로"};
    private static final String[] STORE_SUFFIXES = {"명가", "본점", "하우스", "식당", "키친", "공방"};
    private static final String[] ADDRESS_ALIASES = {"집", "회사", "학교"};

    private static final Map<String, List<String>> MAIN_MENUS = Map.of(
            "한식", List.of("김치찌개", "된장찌개", "부대찌개", "제육볶음", "삼겹살", "돼지갈비", "순대국", "뚝배기불고기"),
            "중식", List.of("짜장면", "짬뽕", "볶음밥", "탕수육", "깐풍기", "마라탕", "마파두부", "유린기"),
            "일식", List.of("모둠초밥", "연어초밥", "라멘", "우동", "돈가스", "규동", "사케동", "냉소바"),
            "양식", List.of("페퍼로니피자", "치즈피자", "까르보나라", "알리오올리오", "치즈버거", "베이컨버거", "리조또", "스테이크"),
            "분식", List.of("떡볶이", "로제떡볶이", "김밥", "참치김밥", "라면", "쫄면", "순대", "모둠튀김"),
            "카페", List.of("아메리카노", "카페라떼", "바닐라라떼", "콜드브루", "치즈케이크", "티라미수", "마카롱", "크로플"));
    private static final List<String> SIDE_MENUS = List.of("감자튀김", "치즈볼", "만두", "계란찜", "샐러드", "어묵", "튀김", "공기밥");
    private static final List<String> DRINK_MENUS = List.of("콜라", "사이다", "제로콜라", "환타", "생수", "식혜", "아이스티");

    private record OptionTemplate(String name, int min, int max, List<String> items, List<Integer> prices) {}

    private static final List<OptionTemplate> OPTION_TEMPLATES = List.of(
            new OptionTemplate("맵기 선택", 1, 1, List.of("순한맛", "보통", "매운맛", "아주 매운맛"), List.of(0, 0, 0, 500)),
            new OptionTemplate("사이즈", 1, 1, List.of("보통", "곱빼기"), List.of(0, 1_000)),
            new OptionTemplate("토핑 추가", 0, 3, List.of("치즈", "계란", "햄", "베이컨", "새우"), List.of(500, 500, 700, 1_000, 1_500)),
            new OptionTemplate("사리 추가", 0, 2, List.of("라면사리", "당면사리", "떡사리"), List.of(1_000, 1_000, 1_000)),
            new OptionTemplate("음료 추가", 0, 1, List.of("콜라", "사이다", "제로콜라"), List.of(2_000, 2_000, 2_000)),
            new OptionTemplate("소스 선택", 0, 2, List.of("머스타드", "칠리", "갈릭", "허니"), List.of(0, 0, 300, 300)),
            new OptionTemplate("곁들임", 0, 6, List.of("단무지", "김치", "피클", "양파", "소스 추가", "수저·포크"), List.of(0, 0, 0, 0, 500, 0)));

    private String randomName() {
        return pick(LAST_NAMES) + pick(FIRST_NAMES);
    }

    // 010-{구분}{번호}: 구분 1=고객, 2=점주, 3=라이더. 번호가 겹치지 않게
    private String phone(int type, int i) {
        return String.format("010-%d%03d-%04d", type, i / 10_000, i % 10_000);
    }

    private Address randomAddress() {
        return randomAddress(pick(DISTRICTS));
    }

    private Address randomAddress(String district) {
        return Address.of(String.format("%05d", 1_000 + random.nextInt(9_000)),
                "서울 " + district + " " + pick(STREETS) + " " + (1 + random.nextInt(300)),
                random.nextInt(100) < 70 ? (1 + random.nextInt(15)) + "층" : null);
    }

    // 서울 대략 범위. scale 7 = DDL decimal(10,7)
    private BigDecimal randomLat() {
        return BigDecimal.valueOf(37.45 + random.nextDouble() * 0.2).setScale(7, RoundingMode.HALF_UP);
    }

    private BigDecimal randomLng() {
        return BigDecimal.valueOf(126.85 + random.nextDouble() * 0.3).setScale(7, RoundingMode.HALF_UP);
    }

    private CustomerGrade randomGrade() {
        int r = random.nextInt(100);
        if (r < 60) return CustomerGrade.BRONZE;
        if (r < 85) return CustomerGrade.SILVER;
        if (r < 97) return CustomerGrade.GOLD;
        return CustomerGrade.VIP;
    }

    private String rootOf(Category category) {
        // path 가 "/루트id/.../" 형태라 첫 번째 id 가 루트다. getReference 프록시라 getPath() 에서 SELECT 가 나간다
        Long rootId = Long.valueOf(category.getPath().split("/")[1]);
        return em.find(Category.class, rootId).getName();
    }

    private <T> T pick(T[] values) {
        return values[random.nextInt(values.length)];
    }

    private static Map<String, List<String>> linked(Object... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") List<String> children = (List<String>) pairs[i + 1];
            map.put((String) pairs[i], children);
        }
        return map;
    }
}
