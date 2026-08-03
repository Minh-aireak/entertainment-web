package com.MyProject.file.file_service.repository;

import com.MyProject.file.file_service.entity.FileMgmt;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileMgmtRepository extends MongoRepository<FileMgmt, String> {
    List<FileMgmt> findAllByOwnerId(String ownerId);
}
