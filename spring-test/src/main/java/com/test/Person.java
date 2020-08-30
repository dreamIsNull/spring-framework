package com.test;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;

import java.beans.PropertyDescriptor;

/**
 * @author : Mr-Z
 * @date : 2020/07/06 22:51
 */
public class Person implements InstantiationAwareBeanPostProcessor {


	private Integer id;
	private String name;
	private Integer age;
	private String birthday;

	private Person2 person2;

	public Person() {
		System.out.println("Person 默认构造");
	}

	public Person(Integer id, String name, Integer age, String birthday) {
		this.id = id;
		this.name = name;
		this.age = age;
		this.birthday = birthday;
		System.out.println("Person 带参数构造");
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getAge() {
		return age;
	}

	public void setAge(Integer age) {
		this.age = age;
	}

	public String getBirthday() {
		return birthday;
	}

	public void setBirthday(String birthday) {
		this.birthday = birthday;
	}

	@Override
	public String toString() {
		return "Person{"+
				"id="+id+
				", name='"+name+'\''+
				", age="+age+
				", birthday='"+birthday+'\''+
				'}';
	}

	public Person2 getPerson2() {
		return person2;
	}

	public void setPerson2(Person2 person2) {
		this.person2 = person2;
	}

	public void say(){
		System.out.printf("My name is %s%n", name);
	}


	@Override
	public Object postProcessBeforeInstantiation(Class <?> beanClass, String beanName) throws BeansException {
		System.out.println("postProcessBeforeInstantiation");
		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		System.out.println("postProcessAfterInstantiation");
		return false;
	}

	@Override
	public PropertyValues postProcessPropertyValues(PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {
		System.out.println("postProcessPropertyValues");
		return null;
	}

	public void destory(){
		System.out.println("destory");
	}
}
