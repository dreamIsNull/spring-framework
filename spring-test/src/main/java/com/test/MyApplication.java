package com.test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * @author : Mr-Z
 * @date : 2020/07/06 22:52
 */
public class MyApplication {
 	public static void main(String[] args) throws IOException {
//		Resource classPathResource = new FileSystemResource("H:/Code/study/spring-framework/spring-test/src/main/resources/beans.xml");
//		Resource classPathResource2 = new FileSystemResource("H:/Code/study/spring-framework/spring-test/src/main/resources/beans2.xml");
		Resource classPathResource = new ClassPathResource("beans.xml");
		BeanFactory bf =new XmlBeanFactory(classPathResource);
		
		Person person = bf.getBean("pee", Person.class);
		System.out.println(person);
//		ClassPathResource resource = new ClassPathResource("beans.xml");
//		System.out.println(resource.exists());
//		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
//		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
//		reader.loadBeanDefinitions(resource);
//		Person person = factory.getBean("pee", Person.class);
//		System.out.println(person);

//		PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
//		pathMatchingResourcePatternResolver.getResources("classpath*:test*/cc*/dd/ff/beans.xml");


//		ResourceLoader
//		person.getClass().getClassLoader();
//		System.out.println(person.getName());
//		ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:beans.xml");
//		ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
//		Person person = context.getBean("pee", Person.class);
//		System.out.println(person.getName());

//		ResourceLoader resourceLoader = new DefaultResourceLoader();
//
//		Resource fileResource1 = resourceLoader.getResource("http://static.iocoder.cn/2446cc9fba90605b691ea250cf340ebb");
//		System.out.println("fileResource1 is FileSystemResource:" + (fileResource1 instanceof FileSystemResource));



//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../core/io/./Resource.class"));
//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../asm/./Edge.class"));
//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../../../asm/./Edge.class"));
//		System.out.println(StringUtils.cleanPath("file:\\dir\\test.txt?argh"));

	}

}
