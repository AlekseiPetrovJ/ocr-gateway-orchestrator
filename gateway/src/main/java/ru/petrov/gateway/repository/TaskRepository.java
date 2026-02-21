package ru.petrov.gateway.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.petrov.gateway.model.FileEntity;
import ru.petrov.gateway.model.ProcessingProfile;
import ru.petrov.gateway.model.TaskEntity;
import ru.petrov.gateway.model.TaskStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    Optional<TaskEntity> findFirstBySourceFileAndProfileAndStatusInOrderByIdDesc(
            FileEntity sourceFile,
            ProcessingProfile profile,
            Collection<TaskStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskEntity t where t.id = :id")
    Optional<TaskEntity> findByIdWithLock(Long id);

    List<TaskEntity> findAllBySourceFileAndProfileAndStatusInAndIdNot(
            FileEntity sourceFile,
            ProcessingProfile profile,
            Collection<TaskStatus> statuses,
            Long pioneerId
    );
}
