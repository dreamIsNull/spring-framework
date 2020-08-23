> 参考网址：<http://cmsblogs.com/?p=2810>

#### 目录

* [1. 检测](#1)
* [2. 检查父类 BeanFactory](#2)
* [3. 类型检查](#3)
* [4. 获取 RootBeanDefinition](#4)
  * [4.1  checkMergedBeanDefinition](#4.1)
* [5. 处理依赖](#5)
  * [5.1 isDependent](#5.1)
  * [5.2 registerDependentBean](#5.2)
  * [5.3 getBean](#5.3)

****

&nbsp;&nbsp;  如果**从单例缓存中没有获取到单例 `Bean` 对象**，则说明两种两种情况

1.  该 `Bean` 的 `scope` 不是 `singleton` 
2.  该 `Bean` 的 `scope` 是 `singleton` ，但是没有初始化完成 

 &nbsp;&nbsp; 针对这两种情况，`Spring` 是如何处理的呢？**统一加载并完成初始化**！这部分内容较长，拆分为两部分 

*  第一部分，主要是一些**检测、`parentBeanFactory` 以及依赖处理** 
*  第二部分，则是**各个 `scope` 的初始化** 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

protected <T> T doGetBean(
		String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
		throws BeansException {
	/*
	 * <1> 返回 bean 名称
	 * 		剥离工厂引用前缀,如果出入&beanName,则去掉&,结果为beanName
	 * 		如果 name 是 alias ，则获取对应映射的 beanName
	 */
	String beanName = transformedBeanName(name);
	Object bean;

	// 从缓存中或者实例工厂中获取 Bean 对象
	Object sharedInstance = getSingleton(beanName);
	if (sharedInstance != null && args == null) {
		if (logger.isDebugEnabled()) {
			if (isSingletonCurrentlyInCreation(beanName)) {
				logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
						"' that is not fully initialized yet - a consequence of a circular reference");
			}
			else {
				logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
			}
		}
		// <2> 完成 FactoryBean 的相关处理，并用来获取 FactoryBean 的处理结果
		bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
	}else {
		// <3> 因为 Spring 只解决单例模式下得循环依赖，在原型模式下如果存在循环依赖则会抛出异常
		if (isPrototypeCurrentlyInCreation(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}

		// <4> 如果容器中没有找到，则从父类容器中加载
		BeanFactory parentBeanFactory = getParentBeanFactory();

		// parentBeanFactory 不为空且 beanDefinitionMap 中不存该 name 的 BeanDefinition
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// 确定原始 beanName
			String nameToLookup = originalBeanName(name);
			if (parentBeanFactory instanceof AbstractBeanFactory) {
				// 如果，父类容器为 AbstractBeanFactory ，直接递归查找
				return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
						nameToLookup, requiredType, args, typeCheckOnly);
			}else if (args != null) {
				// 用明确的 args 从 parentBeanFactory 中，获取 Bean 对象
				return (T) parentBeanFactory.getBean(nameToLookup, args);
			}else {
				// 用明确的 requiredType 从 parentBeanFactory 中，获取 Bean 对象
				return parentBeanFactory.getBean(nameToLookup, requiredType);
			}
		}

		// <5> 如果不是仅仅做类型检查则是创建bean，这里需要记录
		if (!typeCheckOnly) {
			markBeanAsCreated(beanName);
		}

		try {
			// <6> 从容器中获取 beanName 相应的 GenericBeanDefinition 对象，并将其转换为 RootBeanDefinition 对象
			RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
			// 检查给定的合并的 BeanDefinition
			checkMergedBeanDefinition(mbd, beanName, args);

			// <7> 处理所依赖的 bean
			String[] dependsOn = mbd.getDependsOn();
			if (dependsOn != null) {
				for (String dep : dependsOn) {
					// 若给定的依赖 bean 已经注册为依赖给定的 bean
					// 循环依赖的情况
					if (isDependent(beanName, dep)) {
						throw new BeanCreationException(mbd.getResourceDescription(), beanName,
								"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
					}
					// 缓存依赖调用
					registerDependentBean(dep, beanName);
					try {
						// 递归处理依赖 Bean
						getBean(dep);
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanCreationException(mbd.getResourceDescription(), beanName,
								"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
					}
				}
			}

			// <8> bean 实例化
			if (mbd.isSingleton()) {
				/* 单例模式 */

				sharedInstance = getSingleton(beanName, () -> {
					try {
						return createBean(beanName, mbd, args);
					}
					catch (BeansException ex) {
						// 显式从单例缓存中删除 Bean 实例
						// 因为单例模式下为了解决循环依赖，可能他已经存在了，所以销毁它
						destroySingleton(beanName);
						throw ex;
					}
				});
				bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
			}else if (mbd.isPrototype()) {
				/* 原型模式 */

				Object prototypeInstance = null;
				try {
					// <8.1> 加载前置处理
					beforePrototypeCreation(beanName);
					// <8.2> 创建 Bean 对象
					prototypeInstance = createBean(beanName, mbd, args);
				}finally {
					// <8.3> 加载后置处理
					afterPrototypeCreation(beanName);
				}
				bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
			}else {
				/* 从指定的 scope 下创建 bean */

				String scopeName = mbd.getScope();
				if (!StringUtils.hasLength(scopeName)) {
					throw new IllegalStateException("No scope name defined for bean ´" + beanName + "'");
				}
				Scope scope = this.scopes.get(scopeName);
				if (scope == null) {
					throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
				}
				try {
					// 从指定的 scope 下创建 bean
					Object scopedInstance = scope.get(beanName, () -> {
						// 加载前置处理
						beforePrototypeCreation(beanName);
						try {
							// 创建 Bean 对象
							return createBean(beanName, mbd, args);
						}finally {
							// 加载后置处理
							afterPrototypeCreation(beanName);
						}
					});
					// 从 Bean 实例中获取对象
					bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
				}
				catch (IllegalStateException ex) {
					throw new BeanCreationException(beanName,
							"Scope '" + scopeName + "' is not active for the current thread; consider " +
							"defining a scoped proxy for this bean if you intend to refer to it from a singleton",
							ex);
				}
			}
		}
		catch (BeansException ex) {
			cleanupAfterBeanCreationFailure(beanName);
			throw ex;
		}
	}

	// <9> 检查需要的类型是否符合 bean 的实际类型
	if (requiredType != null && !requiredType.isInstance(bean)) {
		try {
			// 执行转换
			T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
			if (convertedBean == null) {
				// 转换失败，抛出 BeanNotOfRequiredTypeException 异常
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
			return convertedBean;
		}
		catch (TypeMismatchException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to convert bean '" + name + "' to required type '" +
						ClassUtils.getQualifiedName(requiredType) + "'", ex);
			}
			throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
		}
	}
	return (T) bean;
}
```

 这里主要处理如下几个部分 

* `<3>` 处，**检测**。若当前 `Bean` 在创建，则抛出 `BeanCurrentlyInCreationException` 异常。 见 [「1. 检测」](#1) 
*  `<4>` 处，如果 `beanDefinitionMap` 中不存在 `beanName` 的 `BeanDefinition`（即在 `Spring bean` 初始化过程中没有加载），则尝试从 `parentBeanFactory` 中加载。 见 [「2. 检查父类 BeanFactory」](#2)  
*  `<5>` 处，判断是否为**类型检查** 。 见 [「3. 类型检查」](#3) 
*  `<6>` 处，从 `mergedBeanDefinitions` 中获取 `beanName` 对应的 `RootBeanDefinition` 对象。如果这个 `BeanDefinition` 是子 `Bean` 的话，则会**合并父类的相关属性**。  见 [「4. 获取 RootBeanDefinition」](#4) 
*  `<7>` 处，**依赖处理** 。 见 [「5. 处理依赖」](#5) 

<span id = "1"></span>
# 1. 检测

&nbsp;&nbsp; `Spring` **只解决单例模式下的循环依赖**，对于**原型模式的循环依赖则是抛出 `BeanCurrentlyInCreationException` 异常**，所以首先检查该 `beanName` 是否处于**原型模式**下的循环依赖 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

// <3> 因为 Spring 只解决单例模式下得循环依赖，在原型模式下如果存在循环依赖则会抛出异常
if (isPrototypeCurrentlyInCreation(beanName)) {
	throw new BeanCurrentlyInCreationException(beanName);
}
```

 &nbsp;&nbsp; 调用 `#isPrototypeCurrentlyInCreation(String beanName)` 方法，**判断当前 `Bean` 是否正在创建中** 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

/**
 * 原型模式正在创建 ThreadLocal
 */
private final ThreadLocal<Object> prototypesCurrentlyInCreation =
		new NamedThreadLocal<>("Prototype beans currently in creation");

protected boolean isPrototypeCurrentlyInCreation(String beanName) {
	Object curVal = this.prototypesCurrentlyInCreation.get();
	return (curVal != null &&
			(curVal.equals(beanName) // 相等
					|| (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));// 包含
}
```

&nbsp;&nbsp;  检测逻辑和单例模式一样，一个“集合”存放着正在创建的 `Bean` ，从该集合中进行判断即可，只不过单例模式的“集合”为 `Set` ，而原型模式的则是 `ThreadLocal` 

<span id = "2"></span>
# 2. 检查父类 BeanFactory

&nbsp;&nbsp;  若 `#containsBeanDefinition(String beanName)` 方法中不存在 `beanName` 相对应的 `BeanDefinition` 对象时，则从 `parentBeanFactory` 中获取 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

// <4> 如果容器中没有找到，则从父类容器中加载
BeanFactory parentBeanFactory = getParentBeanFactory();

// parentBeanFactory 不为空且 beanDefinitionMap 中不存该 name 的 BeanDefinition
if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
	// 确定原始 beanName
	String nameToLookup = originalBeanName(name);
	if (parentBeanFactory instanceof AbstractBeanFactory) {
		// 如果，父类容器为 AbstractBeanFactory ，直接递归查找
		return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
				nameToLookup, requiredType, args, typeCheckOnly);
	}else if (args != null) {
		// 用明确的 args 从 parentBeanFactory 中，获取 Bean 对象
		return (T) parentBeanFactory.getBean(nameToLookup, args);
	}else {
		// 用明确的 requiredType 从 parentBeanFactory 中，获取 Bean 对象
		return parentBeanFactory.getBean(nameToLookup, requiredType);
	}
}

```

&nbsp;&nbsp;  整个过程较为简单，都是委托 `parentBeanFactory` 的 `#getBean(...)` 方法来进行处理，只不过在获取之前对 `breanName` 进行简单的处理，主要是想获取原始的 `beanName` 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

protected String originalBeanName(String name) {
	// <1> 对 name 进行转换，获取真正的 beanName
	String beanName = transformedBeanName(name);
	if (name.startsWith(FACTORY_BEAN_PREFIX)) { // FACTORY_BEAN_PREFIX = "&"
		// <2> 如果 name 是以 “&” 开头的，则加上 “&”
		// 因为在 #transformedBeanName(String name) 方法，将 “&” 去掉了，这里补上
		beanName = FACTORY_BEAN_PREFIX + beanName;
	}
	return beanName;
}
```

*  `<1>` 处，`#transformedBeanName(String name)` 方法，是对 `name` 进行转换，获取真正的 `beanName`。见 [《【Spring 5.0.x】—— 12. IoC 之 Bean 的加载》]()

*  `<2>` 处，如果 `name` 是以 `“&”` 开头的，则加上 `“&”` ，因为在 `#transformedBeanName(String name)` 方法，将 `“&”` 去掉了，这里**补上** 

<span id = "3"></span>
# 3. 类型检查

&nbsp;&nbsp; 方法参数 `typeCheckOnly` ，是用来判断调用 `#getBean(...)` 方法时，表示是否为**仅仅**进行类型检查获取 `Bean` 对象。如果不是仅仅做**类型检查**，而是创建 `Bean` 对象，则需要调用 `#markBeanAsCreated(String beanName)` 方法，进行**记录** 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

/**
 * 存储Bean名称->合并过的 RootBeanDefinition 映射关系
 */
private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

/**
 *  Names of beans that have already been created at least once.
 *
 *  已创建 Bean 的名字集合
 */
private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

protected void markBeanAsCreated(String beanName) {
	// 没有创建
	if (!this.alreadyCreated.contains(beanName)) {
		// 加上全局锁
		synchronized (this.mergedBeanDefinitions) {
			// 再次检查一次：DCL 双检查模式
			if (!this.alreadyCreated.contains(beanName)) {
				// 从 mergedBeanDefinitions 中删除 beanName，并在下次访问时重新创建它
				clearMergedBeanDefinition(beanName);
				// 添加到已创建 bean 集合中
				this.alreadyCreated.add(beanName);
			}
		}
	}
}

/**
 *  从 mergedBeanDefinitions 中删除 beanName
 */
protected void clearMergedBeanDefinition(String beanName) {
    this.mergedBeanDefinitions.remove(beanName);
}
```

<span id = "4"></span>
# 4. 获取 RootBeanDefinition

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

// <6> 从容器中获取 beanName 相应的 GenericBeanDefinition 对象，并将其转换为 RootBeanDefinition 对象
RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
// 检查给定的合并的 BeanDefinition
checkMergedBeanDefinition(mbd, beanName, args);
```

&nbsp;&nbsp;  调用 `#getMergedLocalBeanDefinition(String beanName)` 方法，获取相对应的 `BeanDefinition` 对象 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

/**
 * 存储Bean名称->合并过的 RootBeanDefinition 映射关系
 */
private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
	// 快速从缓存中获取，如果不为空，则直接返回
	RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
	if (mbd != null) {
		return mbd;
	}
	// 获取 RootBeanDefinition，
	// 如果返回的 BeanDefinition 是子类 bean 的话，则合并父类相关属性
	return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
}
```

*  首先，直接从 `mergedBeanDefinitions` 缓存中获取相应的 `RootBeanDefinition` 对象，如果存在则直接返回 

*  否则，调用 `#getMergedBeanDefinition(String beanName, BeanDefinition bd)` 方法，获取 `RootBeanDefinition` 对象。若获取的 `BeanDefinition` 为**子** `BeanDefinition`，则需要合并**父类**的相关属性 

   ```java
   // org.springframework.beans.factory.support.AbstractBeanFactory.java
   
   /**
    * 存储Bean名称->合并过的 RootBeanDefinition 映射关系
    */
   private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);
   
   protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
   		throws BeanDefinitionStoreException {
   
   	return getMergedBeanDefinition(beanName, bd, null);
   }
   
   protected RootBeanDefinition getMergedBeanDefinition(
   		String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
   		throws BeanDefinitionStoreException {
   
   	// 加锁
   	synchronized (this.mergedBeanDefinitions) {
   
   		// 准备一个RootBeanDefinition变量引用，用于记录要构建和最终要返回的BeanDefinition
   		RootBeanDefinition mbd = null;
   
   		if (containingBd == null) {
   			// <1> 检查 mergedBeanDefinitions 缓存中 是否有已合并过的RootBeanDefinition,有就取出
   			mbd = this.mergedBeanDefinitions.get(beanName);
   		}
   
   		// <2> mergedBeanDefinitions 缓存中没有已合并过的RootBeanDefinition，进行处理
   		if (mbd == null) {
   			if (bd.getParentName() == null) {
   				/*
   				 * bd.getParentName() == null，表明无父配置（bd不是一个ChildBeanDefinition）这时直接将当前的 BeanDefinition 升级为 RootBeanDefinition
   				 * 这里有两种情况：
   				 * 1. 一个独立的 GenericBeanDefinition 实例，parentName 属性为null
   				 * 2. 或者是一个 RootBeanDefinition 实例，parentName 属性为null
   				 */
   				if (bd instanceof RootBeanDefinition) {
   					mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
   				}else {
   					mbd = new RootBeanDefinition(bd);
   				}
   			}else {
   				/* bd是一个ChildBeanDefinition */
   
   				BeanDefinition pbd;
   				try {
   					// 获取父类的beanName
   					String parentBeanName = transformedBeanName(bd.getParentName());
   
   					/*
   					 * 判断父类 beanName 与子类 beanName 名称是否相同。
   					 * 若相同，则父类 bean 一定在父容器中。
   					 * 		原因也很简单，容器底层是用 Map 缓存 <beanName, bean> 键值对的。同一个容器下，使用同一个 beanName 映射两个 bean 实例显然是不合适的。
   					 */
   					if (!beanName.equals(parentBeanName)) {
   						pbd = getMergedBeanDefinition(parentBeanName);
   					}else {
   						// 获取父容器，并判断，父容器的类型，若不是 ConfigurableBeanFactory 则判抛出异常
   						BeanFactory parent = getParentBeanFactory();
   						if (parent instanceof ConfigurableBeanFactory) {
   							pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
   						}else {
   							throw new NoSuchBeanDefinitionException(parentBeanName,
   									"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
   									"': cannot be resolved without a ConfigurableBeanFactory parent");
   						}
   					}
   				}
   				catch (NoSuchBeanDefinitionException ex) {
   					throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
   							"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
   				}
   				// 以父 BeanDefinition 的配置信息为蓝本创建 RootBeanDefinition，也就是“已合并的 BeanDefinition”
   				mbd = new RootBeanDefinition(pbd);
   				// 用子 BeanDefinition 中的属性覆盖父 BeanDefinition 中的属性
   				mbd.overrideFrom(bd);
   			}
   
   			// <3> 如果用户未配置 scope 属性，则默认将该属性配置为 singleton
   			if (!StringUtils.hasLength(mbd.getScope())) {
   				mbd.setScope(SCOPE_SINGLETON);
   			}
   
   			// A bean contained in a non-singleton bean cannot be a singleton itself.
   			// Let's correct this on the fly here, since this might be the result of
   			// parent-child merging for the outer bean, in which case the original inner bean
   			// definition will not have inherited the merged outer bean's singleton status.
   			if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
   				mbd.setScope(containingBd.getScope());
   			}
   
   			// <4> 缓存合并后的 BeanDefinition
   			if (containingBd == null && isCacheBeanMetadata()) {
   				this.mergedBeanDefinitions.put(beanName, mbd);
   			}
   		}
   
   		return mbd;
   	}
   }
   ```

   &nbsp;&nbsp; &nbsp;&nbsp; 处理过程如下

   * `<1>`处，检查 `mergedBeanDefinitions` 缓存中 是否有已合并过的`RootBeanDefinition`,有就取出
   * `<2>`处，`mergedBeanDefinitions` 缓存中没有已合并过的`RootBeanDefinition`又分为两步处理
     * `bd.getParentName() == null`，这种情况下表明`bd`不是一个`ChildBeanDefinition`，这时直接将当前的 `BeanDefinition` 升级为 `RootBeanDefinition`
     * `bd.getParentName() != null`，这种情况下表明`bd`是一个`ChildBeanDefinition`，这时将父类的`BeanDefinition` 取出并进行合并
   * `<3>`处，如果用户未配置 `scope` 属性，则默认将该属性配置为 `singleton`
   * `<4>`处，缓存合并后的 `BeanDefinition`(添加到`mergedBeanDefinitions`)

<span id = "4.1"></span>
## 4.1  checkMergedBeanDefinition

&nbsp;&nbsp; 调用 `#checkMergedBeanDefinition()` 方法，检查给定的合并的 `BeanDefinition` 对象 

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args)
		throws BeanDefinitionStoreException {
	if (mbd.isAbstract()) {
		throw new BeanIsAbstractException(beanName);
	}
}
```

<span id = "5"></span>
# 5. 处理依赖

 &nbsp;&nbsp; 如果一个 `Bean` 有依赖 `Bean` 的话，那么在**初始化该 `Bean` 时是需要先初始化它所依赖的 `Bean `**

```java
// org.springframework.beans.factory.support.AbstractBeanFactory.java

// <7> 处理所依赖的 bean
String[] dependsOn = mbd.getDependsOn();
if (dependsOn != null) {
	for (String dep : dependsOn) {
		// <7.1>若给定的依赖 bean 已经注册为依赖给定的 bean
		// 循环依赖的情况
		if (isDependent(beanName, dep)) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
		}
		// 缓存依赖调用
		registerDependentBean(dep, beanName);
		try {
			// 递归处理依赖 Bean
			getBean(dep);
		}
		catch (NoSuchBeanDefinitionException ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
		}
	}
}
```

&nbsp;&nbsp;  这段代码逻辑是：通过迭代的方式依次对依赖 `bean` 进行检测、校验。如果通过，则调用 `#getBean(String beanName)` 方法，实例化**依赖**的 `Bean` 对象 

<span id = "5.1"></span>
## 5.1 isDependent

&nbsp;&nbsp;  `<7.1>` 处，调用 `#isDependent(String beanName, String dependentBeanName)` 方法，是校验该依赖是否已经注册给当前 `Bean`

```java 
// org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.java

/**
 * Map between dependent bean names: bean name to Set of dependent bean names.
 *
 * 保存的是依赖 beanName 之间的映射关系：beanName - > 依赖 beanName 的集合
 */
private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

protected boolean isDependent(String beanName, String dependentBeanName) {
	synchronized (this.dependentBeanMap) {
		return isDependent(beanName, dependentBeanName, null);
	}
}
```

*  `dependentBeanMap` 对象保存的是依赖 `beanName` 之间的映射关系：`beanName` - > 依赖 `beanName` 的集合 

*  同步加锁给 `dependentBeanMap` 对象，然后调用 `#isDependent(String beanName, String dependentBeanName, Set alreadySeen)` 方法，进行校验 

  ```java
  // org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.java
  
  private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
  	// alreadySeen 已经检测的依赖 bean
  	if (alreadySeen != null && alreadySeen.contains(beanName)) {
  		return false;
  	}
  	// 获取原始 beanName
  	String canonicalName = canonicalName(beanName);
  	// 获取当前 beanName 的依赖集合
  	Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
  	if (dependentBeans == null) {
  		return false;
  	}
  	// 存在，则证明存在已经注册的依赖
  	if (dependentBeans.contains(dependentBeanName)) {
  		return true;
  	}
  	// 递归检测依赖
  	for (String transitiveDependency : dependentBeans) {
  		if (alreadySeen == null) {
  			alreadySeen = new HashSet<>();
  		}
  		// 添加到 alreadySeen 中
  		alreadySeen.add(beanName);
  		if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
  			return true;
  		}
  	}
  	return false;
  }
  ```

<span id = "5.2"></span>
## 5.2 registerDependentBean

&nbsp;&nbsp;  `<7.2>` 处，如果校验成功，则调用 `#registerDependentBean(String beanName, String dependentBeanName)` 方法，将**该依赖进行注册，便于在销毁 `Bean` 之前对其进行销毁** 

```java
// org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.java

/**
 * Map between dependent bean names: bean name to Set of dependent bean names.
 *
 * 保存的是依赖 beanName 之间的映射关系：beanName - > 依赖 beanName 的集合
 */
private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

/**
 * Map between depending bean names: bean name to Set of bean names for the bean's dependencies.
 *
 * 保存的是依赖 beanName 之间的映射关系：依赖 beanName - > beanName 的集合
 */
private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);

public void registerDependentBean(String beanName, String dependentBeanName) {
	// 获取 beanName
	String canonicalName = canonicalName(beanName);

	// 添加 <canonicalName, <dependentBeanName>> 到 dependentBeanMap 中
	synchronized (this.dependentBeanMap) {
		Set<String> dependentBeans =
				this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
		if (!dependentBeans.add(dependentBeanName)) {
			return;
		}
	}

	// 添加 <dependentBeanName, <canonicalName>> 到 dependenciesForBeanMap 中
	synchronized (this.dependenciesForBeanMap) {
		Set<String> dependenciesForBean =
				this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
		dependenciesForBean.add(canonicalName);
	}
}
```

&nbsp;&nbsp;  其实将就是该**映射关系保存到两个集合**中：`dependentBeanMap`、`dependenciesForBeanMap` 

<span id = "5.3"></span>
## 5.3 getBean

 &nbsp;&nbsp; `<7.3>` 处，最后调用 `#getBean(String beanName)` 方法，实例化依赖 `Bean` 对象

