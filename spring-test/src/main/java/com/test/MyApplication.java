package com.test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * @author : Mr-Z
 * @date : 2020/07/06 22:52
 */
public class MyApplication {
 	public static void main(String[] args) throws IOException {
//		beanFactoryTest();
//		applicationContextTest();


//		pathMatcherTest();



//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../core/io/./Resource.class"));
//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../asm/./Edge.class"));
//		System.out.println(StringUtils.cleanPath("file:org/springframework/core/../../../asm/./Edge.class"));
//		System.out.println(StringUtils.cleanPath("file:\\dir\\test.txt?argh"));

//		AlternativeJdkIdGenerator alternativeJdkIdGenerator = new AlternativeJdkIdGenerator();
//		System.out.println(alternativeJdkIdGenerator.generateId());
	}
	public static void applicationContextTest() {
// 		new AnnotationConfigApplicationContext(Person.class);
//		ApplicationContext context = new ClassPathXmlApplicationContext("classpath*:/resources/beanFactory.xml");
		ApplicationContext context = new ClassPathXmlApplicationContext("beanFactory.xml");
//		Person person = context.getBean("person", Person.class);
//		System.out.println(person.getName());

	}

	public static void beanFactoryTest() {
//		Resource classPathResource = new FileSystemResource("H:\\Code\\study\\spring-framework\\spring-test\\src\\main\\resources\\beanFactory.xml");
//		Resource classPathResource = new FileSystemResource("H:\\Code\\study\\spring-framework\\spring-test\\src\\main\\resources\\beanFactory.xml");
		Resource classPathResource = new FileSystemResource("beanFactory.xml");
//		Resource classPathResource2 = new FileSystemResource("H:/Code/study/spring-framework/spring-test/src/main/resources/beans2.xml");
//		Resource classPathResource3 = new ClassPathResource("xml/beanFactory.xml");
//		XmlBeanFactory bf =new XmlBeanFactory(classPathResource);

//		Person person = bf.getBean("pee", Person.class);
//		Display display = bf.getBean("display", Display.class);
//		display.display();
//		bf.removeBeanDefinition("person");
//		Person2 person2 = bf.getBean("person2", Person2.class);
//		System.out.println(person2.getName2());
//		System.out.println(person.getPerson2().getName2());
//		System.out.println(person);
//		User user = bf.getBean("user", User.class);
//		System.out.println(user);
	}
	public static void resourceTest() {

	}

	public static void pathMatcherTest() {
//		PathMatcher pathMatcher = new AntPathMatcher();
//
//		// 精确匹配
//		System.out.println(pathMatcher.match("/test", "/test"));
//		System.out.println(pathMatcher.match("test", "/test"));
//
//		//测试通配符?
//		System.out.println(pathMatcher.match("t?st", "test"));
//		System.out.println(pathMatcher.match("te??", "test"));
//		System.out.println(pathMatcher.match("tes?", "tes"));
//		System.out.println(pathMatcher.match("tes?", "testt"));
//
//		//测试通配符*
//		System.out.println(pathMatcher.match("*", "test"));
//		System.out.println(pathMatcher.match("test*", "test"));
//		System.out.println(pathMatcher.match("test/*", "test/Test"));
//		System.out.println(pathMatcher.match("*.*", "test."));
//		System.out.println(pathMatcher.match("*.*", "test.test.test"));
//		System.out.println(pathMatcher.match("test*", "test/")); //注意这里是false 因为路径不能用*匹配
//		System.out.println(pathMatcher.match("test*", "test/t")); //这同理
//		System.out.println(pathMatcher.match("test*aaa", "testblaaab")); //这个是false 因为最后一个b无法匹配了 前面都是能匹配成功的
//
//		//测试通配符** 匹配多级URL
//		System.out.println(pathMatcher.match("/*/**", "/testing/testing"));
//		System.out.println(pathMatcher.match("/**/*", "/testing/testing"));
//		System.out.println(pathMatcher.match("/bla/**/bla", "/bla/testing/testing/bla/bla")); //这里也是true哦
//		System.out.println(pathMatcher.match("/bla*bla/test", "/blaXXXbl/test"));
//
//		System.out.println(pathMatcher.match("/????", "/bala/bla"));
//		System.out.println(pathMatcher.match("/**/*bla", "/bla/bla/bla/bbb"));
//
//		System.out.println(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing/"));
//		System.out.println(pathMatcher.match("/*bla*/**/bla/*", "/XXXblaXXXX/testing/testing/bla/testing"));
//		System.out.println(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing"));
//		System.out.println(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing.jpg"));
//		System.out.println(pathMatcher.match("/foo/bar/**", "/foo/bar"));
//
//		//这个需要特别注意：{}里面的相当于Spring MVC里接受一个参数一样，所以任何东西都会匹配的
//		System.out.println(pathMatcher.match("/{bla}.html", "/testing.html.html"));
//		System.out.println(pathMatcher.match("/{bla}.htm", "/testing.html")); //这样就是false了
	}

}
