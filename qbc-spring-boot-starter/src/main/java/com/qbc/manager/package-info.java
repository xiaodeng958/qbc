/**
 * Manager 层：通用业务处理层，它有如下特征：<br>
 * 1） 对第三方平台封装的层，预处理返回结果及转化异常信息；<br>
 * 2） 对 Service 层通用能力的下沉，如缓存方案、中间件通用处理；<br>
 * 3） 与 DAO 层交互，对多个 DAO 的组合复用。
 * 
 * @author Ma
 */
package com.qbc.manager;