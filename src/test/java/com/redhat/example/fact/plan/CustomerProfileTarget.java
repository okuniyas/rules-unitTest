package com.redhat.example.fact.plan;

import java.util.LinkedHashMap;
import java.util.Map;

public class CustomerProfileTarget {
	private String 金融機関コード;
	private String CX顧客番号;
	private Map<String,Object> map = new LinkedHashMap<String, Object>();;
	
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
	public Map<String,Object> getMap() {
		return map;
	}
	public void setMap(Map<String,Object> map) {
		this.map = map;
	}
}
