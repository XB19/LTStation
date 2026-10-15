package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSAccountRepository extends JpaRepository<GPRSAccount, Integer>, JpaSpecificationExecutor<GPRSAccount> {

    Optional<GPRSAccount> findFirstByUserNameAndPassword(String userName, String password);

    List<GPRSAccount> findByBelongToIdOrId(Integer belongToId, Integer id);
}
