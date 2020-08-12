> 参考网址：<http://cmsblogs.com/?p=2850>

#### 目录

* [1. autowireConstructor](#1)
  * [1.1 instantiate](#1.1)
    * [1.1.1 反射创建 Bean 对象](#1.1.1)
    * [1.1.2 CGLIB 创建 Bean 对象](#1.1.2)
* [2. instantiateBean](#2)
  * [2.1 instantiate](#2.1)
* [3. 总结](#3)

****

&nbsp;&nbsp;  ﻿`#createBeanInstance(String beanName, RootBeanDefinition mbd, Object[] args)` 方法，用于实例化 `Bean` 对象。它会根据不同情况，选择不同的**实例化策略**来完成 `Bean` 的初始化，主要包括 

*  `Supplier` 回调：`#obtainFromSupplier(final String beanName, final RootBeanDefinition mbd)` 方法 
*  工厂方法初始化：`#instantiateUsingFactoryMethod(String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs)` 方法 
*  构造函数自动注入初始化：`#autowireConstructor(final String beanName, final RootBeanDefinition mbd, Constructor[] chosenCtors, final Object[] explicitArgs)` 方法 
*  默认构造函数注入：`#instantiateBean(final String beanName, final RootBeanDefinition mbd)` 方法 

 &nbsp;&nbsp; [《【Spring 5.0.x】—— 17. IoC 之 Bean 的加载：创建 Bean（二）Factory 实例化 bean》]()分析了**前两种** `Supplier` 回调和工厂方法初始化。这里分析**后两种**构造函数注入 

<span id = "1" ></span>
# 1. autowireConstructor

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected BeanWrapper autowireConstructor(
		String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {

	return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
}


// org.springframework.beans.factory.support.ConstructorResolver.java

public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd,
		@Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {

	// 封装 BeanWrapperImpl 对象，并完成初始化
	BeanWrapperImpl bw = new BeanWrapperImpl();
	this.beanFactory.initBeanWrapper(bw);

	// 获得 constructorToUse、argsHolderToUse、argsToUse
	Constructor<?> constructorToUse = null; // 构造函数
	ArgumentsHolder argsHolderToUse = null; // 构造参数
	Object[] argsToUse = null; // 构造参数

	// 确定构造参数
	// 如果 getBean() 已经传递，则直接使用
	if (explicitArgs != null) {
		argsToUse = explicitArgs;
	}else {
		// 尝试从缓存中获取
		Object[] argsToResolve = null;
		synchronized (mbd.constructorArgumentLock) {
			// 缓存中的构造函数或者工厂方法
			constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
			if (constructorToUse != null && mbd.constructorArgumentsResolved) {
				// 缓存中的构造参数
				argsToUse = mbd.resolvedConstructorArguments;
				if (argsToUse == null) {
					argsToResolve = mbd.preparedConstructorArguments;
				}
			}
		}
		// 缓存中存在,则解析存储在 BeanDefinition 中的参数
		// 如给定方法的构造函数 A(int ,int )，则通过此方法后就会把配置文件中的("1","1")转换为 (1,1)
		// 缓存中的值可能是原始值也有可能是最终值
		if (argsToResolve != null) {
			argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve);
		}
	}

	// 没有缓存，则尝试从配置文件中获取参数
	if (constructorToUse == null) {
		/// 是否需要解析构造器
		boolean autowiring = (chosenCtors != null ||
				mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
		// 用于承载解析后的构造函数参数的值
		ConstructorArgumentValues resolvedValues = null;

		int minNrOfArgs;
		if (explicitArgs != null) {
			minNrOfArgs = explicitArgs.length;
		}else {
			// 从 BeanDefinition 中获取构造参数，也就是从配置文件中提取构造参数
			ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
			resolvedValues = new ConstructorArgumentValues();
			// 解析构造函数的参数
			// 将该 bean 的构造函数参数解析为 resolvedValues 对象，其中会涉及到其他 bean
			minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
		}

		// 如果 chosenCtors 未传入，则获取构造方法们
		Constructor<?>[] candidates = chosenCtors;
		if (candidates == null) {
			Class<?> beanClass = mbd.getBeanClass();
			try {
				candidates = (mbd.isNonPublicAccessAllowed() ?
						beanClass.getDeclaredConstructors() : beanClass.getConstructors());
			}
			catch (Throwable ex) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Resolution of declared constructors on bean Class [" + beanClass.getName() +
						"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
			}
		}
		// 对构造函数进行排序处理
		// public 构造函数优先参数数量降序，非public 构造函数参数数量降序
		AutowireUtils.sortConstructors(candidates);

		// 最小参数类型权重
		int minTypeDiffWeight = Integer.MAX_VALUE;
		Set<Constructor<?>> ambiguousConstructors = null;
		LinkedList<UnsatisfiedDependencyException> causes = null;

		// 迭代所有构造函数
		for (Constructor<?> candidate : candidates) {
			// 获取该构造函数的参数类型
			Class<?>[] paramTypes = candidate.getParameterTypes();

			// 如果已经找到选用的构造函数或者需要的参数个数小于当前的构造函数参数个数，则终止。
			// 因为，已经按照参数个数降序排列了
			if (constructorToUse != null && argsToUse.length > paramTypes.length) {
				// Already found greedy constructor that can be satisfied ->
				// do not look any further, there are only less greedy constructors left.
				break;
			}
			// 参数个数不等，继续
			if (paramTypes.length < minNrOfArgs) {
				continue;
			}

			// 参数持有者 ArgumentsHolder 对象
			ArgumentsHolder argsHolder;
			if (resolvedValues != null) {
				try {
					// 注释上获取参数名称
					String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, paramTypes.length);
					if (paramNames == null) {
						// 获取构造函数、方法参数的探测器
						ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
						if (pnd != null) {
							// 通过探测器获取构造函数的参数名称
							paramNames = pnd.getParameterNames(candidate);
						}
					}
					// 根据构造函数和构造参数，创建参数持有者 ArgumentsHolder 对象
					argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
							getUserDeclaredConstructor(candidate), autowiring);
				}
				catch (UnsatisfiedDependencyException ex) {
					// 若发生 UnsatisfiedDependencyException 异常，添加到 causes 中。
					if (logger.isTraceEnabled()) {
						logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
					}
					// Swallow and try next constructor.
					if (causes == null) {
						causes = new LinkedList<>();
					}
					causes.add(ex);
					// continue ，继续执行
					continue;
				}
			}else {
				// 构造函数没有参数
				if (paramTypes.length != explicitArgs.length) {
					continue;
				}
				// 根据 explicitArgs ，创建 ArgumentsHolder 对象
				argsHolder = new ArgumentsHolder(explicitArgs);
			}

			// isLenientConstructorResolution 判断解析构造函数的时候是否以宽松模式还是严格模式
			// 严格模式：解析构造函数时，必须所有的都需要匹配，否则抛出异常
			// 宽松模式：使用具有"最接近的模式"进行匹配
			// typeDiffWeight：类型差异权重
			int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
					argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));

			// 如果它代表着当前最接近的匹配则选择其作为构造函数
			if (typeDiffWeight < minTypeDiffWeight) {
				constructorToUse = candidate;
				argsHolderToUse = argsHolder;
				argsToUse = argsHolder.arguments;
				minTypeDiffWeight = typeDiffWeight;
				ambiguousConstructors = null;
			}else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
				if (ambiguousConstructors == null) {
					ambiguousConstructors = new LinkedHashSet<>();
					ambiguousConstructors.add(constructorToUse);
				}
				ambiguousConstructors.add(candidate);
			}
		}

		// 没有可执行的工厂方法，抛出异常
		if (constructorToUse == null) {
			if (causes != null) {
				UnsatisfiedDependencyException ex = causes.removeLast();
				for (Exception cause : causes) {
					this.beanFactory.onSuppressedException(cause);
				}
				throw ex;
			}
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Could not resolve matching constructor " +
					"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
		}else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Ambiguous constructor matches found in bean '" + beanName + "' " +
					"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
					ambiguousConstructors);
		}

		if (explicitArgs == null) {
			// 将解析的构造函数加入缓存
			argsHolderToUse.storeCache(mbd, constructorToUse);
		}
	}

	try {
		final InstantiationStrategy strategy = beanFactory.getInstantiationStrategy();
		Object beanInstance;

		if (System.getSecurityManager() != null) {
			final Constructor<?> ctorToUse = constructorToUse;
			final Object[] argumentsToUse = argsToUse;
			beanInstance = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
					strategy.instantiate(mbd, beanName, beanFactory, ctorToUse, argumentsToUse),
					beanFactory.getAccessControlContext());
		}
		else {
			beanInstance = strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
		}

		// 创建 Bean 对象，并设置到 bw(BeanWrapperImpl) 中
		bw.setBeanInstance(beanInstance);
		return bw;
	}
	catch (Throwable ex) {
		throw new BeanCreationException(mbd.getResourceDescription(), beanName,
				"Bean instantiation via constructor failed", ex);
	}
}
```

&nbsp;&nbsp; 首先确定**构造函数参数**、**构造函数**，然后调用相应的**初始化策略**进行 `bean` 的初始化。如何确定构造函数、构造参数，该部分逻辑和 `#instantiateUsingFactoryMethod(...)` 方法，基本一致。见[【Spring 5.0.x】—— 17. IoC 之 Bean 的加载：创建 Bean（二）Factory 实例化 bean]()

&nbsp;&nbsp; 这里我们重点分析**初始化策略** 

<span id = "1.1" ></span>
## 1.1 instantiate

```java
// org.springframework.beans.factory.support.ConstructorResolver.java

// 获取实例化 Bean 的策略 InstantiationStrategy 对象
final InstantiationStrategy strategy = beanFactory.getInstantiationStrategy();
Object beanInstance;

if (System.getSecurityManager() != null) {
	final Constructor<?> ctorToUse = constructorToUse;
	final Object[] argumentsToUse = argsToUse;
	beanInstance = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
			strategy.instantiate(mbd, beanName, beanFactory, ctorToUse, argumentsToUse),
			beanFactory.getAccessControlContext());
}else {
	beanInstance = strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
}
```

* 首先，是获取实例化 `Bean` 的策略 `InstantiationStrategy` 对象 
* 然后，调用其 `#instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner, Constructor ctor, Object... args)` 方法，该方法在 `SimpleInstantiationStrategy` 中实现 

```java
// org.springframework.beans.factory.support.SimpleInstantiationStrategy.java

@Override
public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
		final Constructor<?> ctor, @Nullable Object... args) {

	// <x1> 没有覆盖，直接使用反射实例化即可
	if (!bd.hasMethodOverrides()) {
		if (System.getSecurityManager() != null) {
			// 设置构造方法，可访问
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(ctor);
				return null;
			});
		}
		// 通过 BeanUtils 直接使用构造器对象实例化 Bean 对象
		return (args != null ? BeanUtils.instantiateClass(ctor, args) : BeanUtils.instantiateClass(ctor));
	}else {
		// <x2> 生成 CGLIB 创建的子类对象
		return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
	}
}
```

*  `<x1>` 如果该 `bean` 没有配置 `lookup-method`、`replaced-method` 标签或者 `@Lookup` 注解，则直接通过**反射**的方式实例化 `Bean` 对象即可，方便快捷。见 [「1.1.1 反射创建 Bean 对象」](#1.1.1) 

*  `<x2>` 如果**存在需要覆盖的方法或者动态替换的方法**时，则需要使用 `CGLIB` 进行动态代理，因为可以在创建代理的同时将动态方法织入类中。见 [「1.1.2 CGLIB 创建 Bean 对象」](#1.1.2)

<span id = "1.1.1" ></span>
### 1.1.1 反射创建 Bean 对象

&nbsp;&nbsp;  调用工具类 `BeanUtils` 的 `#instantiateClass(Constructor ctor, Object... args)` 方法，完成反射工作，创建对象 

```java
// org.springframework.beans.BeanUtils.java

public static <T> T instantiateClass(Constructor<T> ctor, Object... args) throws BeanInstantiationException {
	Assert.notNull(ctor, "Constructor must not be null");
	try {
		// 设置构造方法，可访问
		ReflectionUtils.makeAccessible(ctor);
		// 使用构造方法，创建对象
		return (KotlinDetector.isKotlinType(ctor.getDeclaringClass()) ?
				KotlinDelegate.instantiateClass(ctor, args) : ctor.newInstance(args));

	// 各种异常的翻译，最终统一抛出 BeanInstantiationException 异常
	}catch (InstantiationException ex) {
		throw new BeanInstantiationException(ctor, "Is it an abstract class?", ex);
	}catch (IllegalAccessException ex) {
		throw new BeanInstantiationException(ctor, "Is the constructor accessible?", ex);
	}catch (IllegalArgumentException ex) {
		throw new BeanInstantiationException(ctor, "Illegal arguments for constructor", ex);
	}catch (InvocationTargetException ex) {
		throw new BeanInstantiationException(ctor, "Constructor threw exception", ex.getTargetException());
	}
}
```

<span id = "1.1.2" ></span>
### 1.1.2 CGLIB 创建 Bean 对象

```java
// org.springframework.beans.factory.support.SimpleInstantiationStrategy.java

protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName,
		BeanFactory owner, @Nullable Constructor<?> ctor, @Nullable Object... args) {

	throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
}
```

&nbsp;&nbsp;  方法默认是**没有实现**的，具体过程由其子类 `org.springframework.beans.factory.support.CglibSubclassingInstantiationStrategy` 来实现 

```java
// org.springframework.beans.factory.support.CglibSubclassingInstantiationStrategy.java

@Override
protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
	return instantiateWithMethodInjection(bd, beanName, owner, null);
}

@Override
protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
		@Nullable Constructor<?> ctor, @Nullable Object... args) {

	// 通过CGLIB生成一个子类对象
	return new CglibSubclassCreator(bd, owner).instantiate(ctor, args);
}
```

&nbsp;&nbsp;  创建一个 `CglibSubclassCreator` 对象，后调用其 `#instantiate(Constructor ctor, Object... args)` 方法，生成其子类对象 

```java
// org.springframework.beans.factory.support.CglibSubclassingInstantiationStrategy.java

public Object instantiate(@Nullable Constructor<?> ctor, @Nullable Object... args) {
	// 通过 Cglib 创建一个代理类
	Class<?> subclass = createEnhancedSubclass(this.beanDefinition);
	Object instance;
	// 没有构造器，通过 BeanUtils 使用默认构造器创建一个bean实例
	if (ctor == null) {
		instance = BeanUtils.instantiateClass(subclass);
	}
	else {
		try {
			// 获取代理类对应的构造器对象，并实例化 bean
			Constructor<?> enhancedSubclassConstructor = subclass.getConstructor(ctor.getParameterTypes());
			instance = enhancedSubclassConstructor.newInstance(args);
		}
		catch (Exception ex) {
			throw new BeanInstantiationException(this.beanDefinition.getBeanClass(),
					"Failed to invoke constructor for CGLIB enhanced subclass [" + subclass.getName() + "]", ex);
		}
	}
	// 为了避免 memory leaks 异常，直接在 bean 实例上设置回调对象
	Factory factory = (Factory) instance;
	factory.setCallbacks(new Callback[] {NoOp.INSTANCE,
			new LookupOverrideMethodInterceptor(this.beanDefinition, this.owner),
			new ReplaceOverrideMethodInterceptor(this.beanDefinition, this.owner)});
	return instance;
}
```

> `CglibSubclassCreator`为`CglibSubclassingInstantiationStrategy`的内部类

<span id = "2" ></span>
# 2. instantiateBean

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
	try {
		Object beanInstance;
		// 安全模式
		if (System.getSecurityManager() != null) {
			beanInstance = AccessController.doPrivileged(
					(PrivilegedAction<Object>) () ->
							// 获得 InstantiationStrategy 对象，并使用它，创建 Bean 对象
							getInstantiationStrategy().instantiate(mbd, beanName, this),
					getAccessControlContext());
		}else {
			// 获得 InstantiationStrategy 对象，并使用它，创建 Bean 对象
			beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
		}
		// 封装 BeanWrapperImpl  并完成初始化
		BeanWrapper bw = new BeanWrapperImpl(beanInstance);
		initBeanWrapper(bw);
		return bw;
	}
	catch (Throwable ex) {
		throw new BeanCreationException(
				mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
	}
}
```

* 首先，获取实例化 `Bean` 的策略 `InstantiationStrategy` 对象 
* 然后，调用其 `#instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner, Constructor ctor, Object... args)` 方法
* 最后封装为`BeanWrapper`类返回

<span id = "2.1" ></span>
## 2.1 instantiate

```java
// org.springframework.beans.factory.support.CglibSubclassingInstantiationStrategy.java

@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		// 没有覆盖，直接使用反射实例化即可
		if (!bd.hasMethodOverrides()) {
			Constructor<?> constructorToUse;
			synchronized (bd.constructorArgumentLock) {
				// 获得构造方法 constructorToUse
				constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse == null) {
					final Class<?> clazz = bd.getBeanClass();
					if (clazz.isInterface()) {
						// 如果是接口，抛出 BeanInstantiationException 异常
						throw new BeanInstantiationException(clazz, "Specified class is an interface");
					}
					try {
						// 从 clazz 中，获得构造方法
						if (System.getSecurityManager() != null) {
							// 安全模式
							constructorToUse = AccessController.doPrivileged(
									(PrivilegedExceptionAction<Constructor<?>>) clazz::getDeclaredConstructor);
						}
						else {
							constructorToUse = clazz.getDeclaredConstructor();
						}
						// 标记 resolvedConstructorOrFactoryMethod 属性
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					}
					catch (Throwable ex) {
						throw new BeanInstantiationException(clazz, "No default constructor found", ex);
					}
				}
			}
			// 通过 BeanUtils 直接使用构造器对象实例化 Bean 对象
			return BeanUtils.instantiateClass(constructorToUse);
		}else {
			/// 生成 CGLIB 创建的子类对象
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}
```

<span id = "3" ></span>
# 3. 总结

&nbsp;&nbsp;   `#createBeanInstance(...)` 方法的具体逻辑就是**选择合适实例化策略**来为 `bean` 创建实例对象，具体的策略有 

*  `Supplier` 回调方式 
*  工厂方法初始化 
*  构造函数自动注入初始化 
*  默认构造函数注入 

&nbsp;&nbsp;  其中，**工厂方法初始化**和**构造函数自动注入初始化**两种方式**最为复杂**，主要是因为**构造函数**和**构造参数**的不确定性，`Spring` 需要花大量的精力来确定构造函数和构造参数，如果确定了则好办，直接选择**实例化策略**即可 

&nbsp;&nbsp;  当然，在实例化的时候会根据**是否有需要覆盖或者动态替换掉的方法**，因为存在覆盖或者织入的话需要创建动态代理将方法织入，这个时候就只能选择 `CGLIB` 的方式来实例化，否则直接利用反射的方式即可，方便快捷 