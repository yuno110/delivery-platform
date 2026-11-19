package com.example.jpa.store;

import com.example.jpa.store.dto.StoreSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;

    public List<StoreSummaryResponse> findStores() {
        return storeRepository.findAll().stream()
                .map(StoreSummaryResponse::from)
                .toList();
    }


    // A. 페치 조인 (SQL 1번)
    public List<StoreSummaryResponse> findStoresWithFetchJoin() {
        return storeRepository.findAllWithFetchJoin().stream()
                .map(StoreSummaryResponse::from)
                .toList();
    }

    // B. @EntityGraph (SQL 1번)
    public List<StoreSummaryResponse> findStoresWithEntityGraph() {
        return storeRepository.findAllWithEntityGraph().stream()
                .map(StoreSummaryResponse::from)
                .toList();
    }




}
