package com.gongbang.file.repository;

import com.gongbang.file.domain.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;

/** 업로드 파일 메타데이터 저장소. */
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {}
