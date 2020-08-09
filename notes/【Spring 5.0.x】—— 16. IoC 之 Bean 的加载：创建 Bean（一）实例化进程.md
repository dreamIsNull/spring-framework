> 参考网址：<http://cmsblogs.com/?p=2846>

#### 目录

* [](#)

****

# 1. createBean 抽象方法

&nbsp;&nbsp; 在 [《【Spring 5.0.x】—— 15. IoC 之 Bean 的加载：分析各 scope 的 Bean 创建》]() 中，有一个核心方法没有讲到， `#createBean(String beanName, RootBeanDefinition mbd, Object[] args)` 方法 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;
```

*  该方法定义在 `AbstractBeanFactory` 中，其含义是**根据给定的 `BeanDefinition` 和 `args` 实例化一个 `Bean` 对象** 
*  如果该 `BeanDefinition` 存在父类，则该 `BeanDefinition` **已经**合并了父类的属性 
*  **所有 `Bean` 实例的创建，都会委托给该方法实现** 
*  该方法接受三个方法参数 
  *  `beanName` ：`bean` 的名字 
  *  `mbd` ：已经合并了父类属性的（如果有的话）`BeanDefinition` 对象 
  *  `args` ：用于构造函数或者工厂方法创建 `Bean` 实例对象的参数 

# 2. createBean 默认实现

 &nbsp;&nbsp; 该抽象方法的**默认实现**是在类 `AbstractAutowireCapableBeanFactory`中实现 

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

@Override
protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
		throws BeanCreationException {

	if (logger.isDebugEnabled()) {
		logger.debug("Creating instance of bean '" + beanName + "'");
	}
	RootBeanDefinition mbdToUse = mbd;

	// <1> 确保此时的 bean 已经被解析了
	// 如果获取的class 属性不为null，则克隆该 BeanDefinition
	// 主要是因为该动态解析的 class 无法保存到到共享的 BeanDefinition
	Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
	if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
		mbdToUse = new RootBeanDefinition(mbd);
		mbdToUse.setBeanClass(resolvedClass);
	}

	try {
		// <2> 验证和准备覆盖方法
		mbdToUse.prepareMethodOverrides();
	}
	catch (BeanDefinitionValidationException ex) {
		throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
				beanName, "Validation of method overrides failed", ex);
	}

	try {
		// <3> 实例化的前置处理
		// 给 BeanPostProcessors 一个机会用来返回一个代理类而不是真正的类实例
		// AOP 的功能就是基于这个地方
		Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
		if (bean != null) {
			return bean;
		}
	}
	catch (Throwable ex) {
		throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
				"BeanPostProcessor before instantiation of bean failed", ex);
	}

	try {
		// <4> 创建 Bean 对象
		Object beanInstance = doCreateBean(beanName, mbdToUse, args);
		if (logger.isDebugEnabled()) {
			logger.debug("Finished creating instance of bean '" + beanName + "'");
		}
		return beanInstance;
	}
	catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
		// A previously detected exception with proper bean creation context already,
		// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
		throw ex;
	}
	catch (Throwable ex) {
		throw new BeanCreationException(
				mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
	}
}
```

&nbsp;&nbsp; 过程如下

*  `<1>` 处，解析指定 `BeanDefinition` 的 `class` 属性 。见  [「2.1 解析指定 BeanDefinition 的 class」](#2.1) 
*  `<2>` 处，处理 `override` 属性 。见  [「2.2 处理 override 属性」](#2.2) 
*  `<3>` 处，实例化的**前置处理** 。见  [「2.3 实例化的前置处理」](#2.3) 
*  `<4>` 处，创建 Bean 对象 。见  [「2.4 创建 Bean」](#2.4) 

## 2.1 解析指定 BeanDefinition 的 class

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

// <1> 确保此时的 bean 已经被解析了
// 如果获取的class 属性不为null，则克隆该 BeanDefinition
// 主要是因为该动态解析的 class 无法保存到到共享的 BeanDefinition
Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
	mbdToUse = new RootBeanDefinition(mbd);
	mbdToUse.setBeanClass(resolvedClass);
}
```

