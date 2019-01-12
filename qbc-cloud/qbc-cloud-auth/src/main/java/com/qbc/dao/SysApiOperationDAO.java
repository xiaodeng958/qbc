package com.qbc.dao;

import org.springframework.stereotype.Repository;

import com.slyak.spring.jpa.GenericJpaRepository;

/**
 * 系统API操作表数据访问类
 *
 * @author Ma
 */
@Repository
public interface SysApiOperationDAO extends GenericJpaRepository<SysApiOperationDO, Long> {
	
}