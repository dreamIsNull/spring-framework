package com.test.car;

/**
 * @author : Mr-Z
 * @date : 2020/08/29 22:53
 */
public abstract class Display {

	public void display(){
		getCar().display();
	}

	public abstract Car getCar();

}
