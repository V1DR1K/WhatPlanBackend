package com.wherefood.repo;

import com.wherefood.domain.CoupleScopedEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/** Repository contract for private rows; deliberately omits unscoped read/delete methods. */
@NoRepositoryBean
public interface CoupleScopedRepository<T extends CoupleScopedEntity> extends Repository<T, Long> {
    <S extends T> S save(S entity);

    <S extends T> List<S> saveAll(Iterable<S> entities);

    Optional<T> findByIdAndCoupleId(Long id, UUID coupleId);

    List<T> findAllByCoupleId(UUID coupleId);

    List<T> findAllByIdInAndCoupleId(Collection<Long> ids, UUID coupleId);

    boolean existsByIdAndCoupleId(Long id, UUID coupleId);

    void delete(T entity);

    void flush();
}
