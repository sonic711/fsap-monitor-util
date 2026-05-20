package com.fsap.monitor.sftp.vo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.fsap.monitor.sftp.transfer.ConnectInfo;

/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2023 All Rights Reserved. <br>
 * 日期：2023/03/02<br>
 * 作者：KaRno<br>
 * 程式代號: RemoteInfo.java<br>
 * 程式說明: 遠端連線資訊<br>
 * ======================================================================
 */
@Component
@ConfigurationProperties("remote")
public class RemoteInfo implements ConnectInfo {

	/** 資料序號 */
	private String oid;
	/** 連線編號 */
	private String id;
	/** 連線分類 */
	private String type;
	/** 連線環境分類 */
	private String env;
	/** 服務連線位置 */
	private String url;
	/** 通訊協定 */
	private String protocol;
	/** 埠號 */
	private Integer port;
	/** 連線名稱 */
	private String name;
	/** 預設路徑或目錄 */
	private String defPath;
	/** 連線用戶 */
	private String username;
	/** 連線驗證碼 */
	private String code;
	/** 備註說明 */
	private String remark;

	public String getOid() {
		return oid;
	}

	public void setOid(String oid) {
		this.oid = oid;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getEnv() {
		return env;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	@Override
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDefPath() {
		return defPath;
	}

	public void setDefPath(String defPath) {
		this.defPath = defPath;
	}

	@Override
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

}
