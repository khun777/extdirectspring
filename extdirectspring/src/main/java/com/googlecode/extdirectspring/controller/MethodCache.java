/**
 * Copyright 2010 Ralph Schaer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.extdirectspring.controller;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.googlecode.extdirectspring.util.ExtDirectSpringUtil;

/**
 * A simple cache for methods
 * 
 * @author Ralph Schaer
 */
public enum MethodCache {

  /**
   * Singleton enum pattern
   */
  INSTANCE;

  private Map<Key, Method> cache;

  private MethodCache() {
    cache = new ConcurrentHashMap<Key, Method>();
  }

  /**
   * Put a method into the MethodCache.
   * 
   * @param beanName The name of the bean
   * @param methodName The name of the method
   * @param method The method
   */
  public void put(String beanName, String methodName, Method method) {
    cache.put(new Key(beanName, methodName), method);
  }

  /**
   * Get a method from the MethodCache. 
   * 
   * @param beanName The name of the bean
   * @param methodName the name of the method
   * @return the found method. Returns null if the method is not in the cache
   */
  public Method get(String beanName, String methodName) {
    return cache.get(new Key(beanName, methodName));
  }

}

final class Key {

  private String beanName;
  private String methodName;

  public Key(String beanName, String methodName) {
    this.beanName = beanName;
    this.methodName = methodName;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Key)) {
      return false;
    }

    Key other = (Key)o;
    return (ExtDirectSpringUtil.equal(beanName, other.beanName) && ExtDirectSpringUtil.equal(methodName, other.methodName));
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[]{beanName, methodName});
  }

}