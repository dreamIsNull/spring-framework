package com.test;

/**
 * @author : Mr-Z
 * @date : 2020/08/23 17:28
 */
public class Person2 {

	private String name2;
	private Integer age2;

	public Person2() {
		System.out.println("Person2 默认构造");
	}

	public Person2(String name2, Integer age2) {
		this.name2 = name2;
		this.age2 = age2;
		System.out.println("Person2 带参数构造");
	}

	public String getName2() {
		return name2;
	}

	public void setName2(String name2) {
		this.name2 = name2;
	}

	public Integer getAge2() {
		return age2;
	}

	public void setAge2(Integer age2) {
		this.age2 = age2;
	}
}
