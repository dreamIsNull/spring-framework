package com.test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author : Mr-Z
 * @date : 2020/07/06 22:52
 */
public class MyApplication {
	public static void main(String[] args) {
		ApplicationContext context =
				new ClassPathXmlApplicationContext("classpath*:application.xml");
		Person person = context.getBean("person", Person.class);
		System.out.println(person.getName());

	}

}
