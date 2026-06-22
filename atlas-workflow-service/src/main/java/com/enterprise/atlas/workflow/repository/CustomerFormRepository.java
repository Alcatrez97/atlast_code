package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.CustomerForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerFormRepository extends JpaRepository<CustomerForm, String> {

    @Query("SELECT cf FROM CustomerForm cf WHERE " +
           "(:status IS NULL OR :status = '' OR LOWER(cf.formStatus) LIKE LOWER(CONCAT('%', :status, '%'))) AND " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(cf.id) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(cf.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(cf.formStatus) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CustomerForm> findAllWithFilters(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);
}
