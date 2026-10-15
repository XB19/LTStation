package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_file (origine SQL Server : Supplier_File). */
@Getter
@Setter
@Entity
@Table(name = "supplier_file")
public class SupplierFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "suffix_name")
    private String suffixName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "original_name")
    private String originalName;

}
