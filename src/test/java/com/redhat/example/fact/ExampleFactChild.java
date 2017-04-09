package com.redhat.example.fact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonBackReference;

public class ExampleFactChild {

	@JsonBackReference
	ExampleFactParent parent;

	private String id;
	private String name;
	private BigDecimal attrBigDecimal = BigDecimal.ZERO;
	private BigDecimal attrBigDecimalPlusOne;
	private List<String> strList = new ArrayList<String>();
	private List<BigDecimal> bdList = new ArrayList<BigDecimal>();
	private List<Date> dateList = new ArrayList<Date>();
	private List<Integer> intList = new ArrayList<Integer>();
	private List<Double> doubleList = new ArrayList<Double>();
	private Map<Integer, String> mapAttr = new TreeMap<Integer, String>();
	private List<ExampleFactSon> sonList = new LinkedList<ExampleFactSon>();
	
	public ExampleFactParent getParent() {
		return parent;
	}
	public void setParent(ExampleFactParent parent) {
		this.parent = parent;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public BigDecimal getAttrBigDecimal() {
		return attrBigDecimal;
	}
	public void setAttrBigDecimal(BigDecimal attrBigDecimal) {
		this.attrBigDecimal = attrBigDecimal;
	}
	public BigDecimal getAttrBigDecimalPlusOne() {
		return attrBigDecimalPlusOne;
	}
	public void setAttrBigDecimalPlusOne(BigDecimal attrBigDecimalPlusOne) {
		this.attrBigDecimalPlusOne = attrBigDecimalPlusOne;
	}
	public List<String> getStrList() {
		return strList;
	}
	public void setStrList(List<String> strList) {
		this.strList = strList;
	}
	public List<BigDecimal> getBdList() {
		return bdList;
	}
	public void setBdList(List<BigDecimal> bdList) {
		this.bdList = bdList;
	}
	public List<Date> getDateList() {
		return dateList;
	}
	public void setDateList(List<Date> dateList) {
		this.dateList = dateList;
	}
	public List<Integer> getIntList() {
		return intList;
	}
	public void setIntList(List<Integer> intList) {
		this.intList = intList;
	}
	public List<Double> getDoubleList() {
		return doubleList;
	}
	public void setDoubleList(List<Double> doubleList) {
		this.doubleList = doubleList;
	}
	public Map<Integer, String> getMapAttr() {
		return mapAttr;
	}
	public void setMapAttr(Map<Integer, String> mapAttr) {
		this.mapAttr = mapAttr;
	}
	public List<ExampleFactSon> getSonList() {
		return sonList;
	}
	public void setSonList(List<ExampleFactSon> sonList) {
		this.sonList = sonList;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((attrBigDecimal == null) ? 0 : attrBigDecimal.hashCode());
		result = prime * result + ((attrBigDecimalPlusOne == null) ? 0 : attrBigDecimalPlusOne.hashCode());
		result = prime * result + ((bdList == null) ? 0 : bdList.hashCode());
		result = prime * result + ((dateList == null) ? 0 : dateList.hashCode());
		result = prime * result + ((doubleList == null) ? 0 : doubleList.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((intList == null) ? 0 : intList.hashCode());
		result = prime * result + ((mapAttr == null) ? 0 : mapAttr.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((sonList == null) ? 0 : sonList.hashCode());
		result = prime * result + ((strList == null) ? 0 : strList.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ExampleFactChild other = (ExampleFactChild) obj;
		if (attrBigDecimal == null) {
			if (other.attrBigDecimal != null)
				return false;
		} else if (!attrBigDecimal.equals(other.attrBigDecimal))
			return false;
		if (attrBigDecimalPlusOne == null) {
			if (other.attrBigDecimalPlusOne != null)
				return false;
		} else if (!attrBigDecimalPlusOne.equals(other.attrBigDecimalPlusOne))
			return false;
		if (bdList == null) {
			if (other.bdList != null)
				return false;
		} else if (!bdList.equals(other.bdList))
			return false;
		if (dateList == null) {
			if (other.dateList != null)
				return false;
		} else if (!dateList.equals(other.dateList))
			return false;
		if (doubleList == null) {
			if (other.doubleList != null)
				return false;
		} else if (!doubleList.equals(other.doubleList))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (intList == null) {
			if (other.intList != null)
				return false;
		} else if (!intList.equals(other.intList))
			return false;
		if (mapAttr == null) {
			if (other.mapAttr != null)
				return false;
		} else if (!mapAttr.equals(other.mapAttr))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (sonList == null) {
			if (other.sonList != null)
				return false;
		} else if (!sonList.equals(other.sonList))
			return false;
		if (strList == null) {
			if (other.strList != null)
				return false;
		} else if (!strList.equals(other.strList))
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "ExampleFactChild [id=" + id + ", name=" + name + ", attrBigDecimal=" + attrBigDecimal
				+ ", attrBigDecimalPlusOne=" + attrBigDecimalPlusOne + ", strList=" + strList + ", bdList=" + bdList
				+ ", dateList=" + dateList + ", intList=" + intList + ", doubleList=" + doubleList + ", mapAttr="
				+ mapAttr + ", sonList=" + sonList + "]";
	}
}
