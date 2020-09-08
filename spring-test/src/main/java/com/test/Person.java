package com.test;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;

/**
 * @author : Mr-Z
 * @date : 2020/07/06 22:51
 */
public class Person implements InstantiationAwareBeanPostProcessor, BeanPostProcessor {


	private Integer id;
	private String name;
	private Integer age;
	private String birthday;

	private int aa;
	private Integer bb;

	private int[] aaL;
	private Integer[] bbL;

	private ArrayList <Object> list;

	private Person2 person3;
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


	public int getAa() {
		return aa;
	}

	public void setAa(int aa) {
		this.aa = aa;
	}

	public Integer getBb() {
		return bb;
	}

	public void setBb(Integer bb) {
		this.bb = bb;
	}

	public int[] getAaL() {
		return aaL;
	}

	public void setAaL(int[] aaL) {
		this.aaL = aaL;
	}

	public Integer[] getBbL() {
		return bbL;
	}

	public void setBbL(Integer[] bbL) {
		this.bbL = bbL;
	}

	public ArrayList <Object> getList() {
		return list;
	}

	public void setList(ArrayList <Object> list) {
		this.list = list;
	}

	public Person2 getPerson3() {
		return person3;
	}

	public void setPerson3(Person2 person3) {
		this.person3 = person3;
	}


	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("postProcessBeforeInitialization");
		return null;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("postProcessAfterInitialization");
		return null;
	}
}
