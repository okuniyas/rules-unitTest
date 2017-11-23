package com.redhat.example.fact.plan;

import java.util.LinkedHashMap;
import java.util.Map;

public class FactInfo {
	private String 金融機関コード;
	private String CX顧客番号;
	private String データ発生日;
	private Map<String, Object> 属性Map = new LinkedHashMap<String, Object>();;
	
	public String get金融機関コード() {
		return 金融機関コード;
	}
	public void set金融機関コード(String 金融機関コード) {
		this.金融機関コード = 金融機関コード;
	}
	public String getCX顧客番号() {
		return CX顧客番号;
	}
	public void setCX顧客番号(String cX顧客番号) {
		CX顧客番号 = cX顧客番号;
	}
	public String getデータ発生日() {
		return データ発生日;
	}
	public void setデータ発生日(String データ発生日) {
		this.データ発生日 = データ発生日;
	}
	public Map<String, Object> get属性Map() {
		return 属性Map;
	}
	public void set属性Map(Map<String, Object> 属性Map) {
		this.属性Map = 属性Map;
	}
}
