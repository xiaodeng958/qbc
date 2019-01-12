package com.qbc.service.core;

import org.springframework.beans.factory.annotation.Autowired;

import com.qbc.api.annotation.Api;
import com.qbc.api.annotation.ApiOperation;
import com.qbc.dto.core.ApplicationDTO;
import com.qbc.manager.core.ApiManageer;

/**
 * 应用信息服务
 *
 * @author Ma
 */
@Api
public class ApplicationService {

	@Autowired
	private ApiManageer apiManageer;

	@ApiOperation
	public ApplicationDTO info() {
		return apiManageer.getApplicationDTO();
	}

}
