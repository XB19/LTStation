package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Employees;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmployeesRepository extends JpaRepository<Employees, Integer>, JpaSpecificationExecutor<Employees> {

    /** Connexion employé : l'original stocke l'identifiant dans Address et le mot de passe dans Memo. */
    Optional<Employees> findFirstByAddressAndMemoAndIsUsingTrue(String address, String memo);
}
