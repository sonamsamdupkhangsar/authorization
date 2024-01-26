package me.sonam.auth.jpa.repo;

import java.util.List;
import java.util.Optional;

import me.sonam.auth.jpa.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, String> {
    Optional<Client> findByClientId(String clientId);
}

