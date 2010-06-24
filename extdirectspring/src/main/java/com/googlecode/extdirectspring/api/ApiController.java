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

package com.googlecode.extdirectspring.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import com.googlecode.extdirectspring.annotation.ExtDirectMethod;
import com.googlecode.extdirectspring.annotation.ExtDirectPollMethod;
import com.googlecode.extdirectspring.annotation.ExtDirectStoreModifyMethod;
import com.googlecode.extdirectspring.annotation.ExtDirectStoreReadMethod;
import com.googlecode.extdirectspring.util.ExtDirectSpringUtil;
import com.googlecode.extdirectspring.util.SupportedParameters;

/**
 * 
 * 
 * @author Ralph Schaer
 */
@Controller
public class ApiController implements ApplicationContextAware {

  private ApplicationContext context;

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    this.context = context;
  }

  @RequestMapping(value = {"/api.js", "/api-debug.js"}, method = RequestMethod.GET)
  @ResponseBody
  public String api(@RequestParam(value = "apiNs", required = false, defaultValue = "Ext.app") final String apiNs,
      @RequestParam(value = "actionNs", required = false) final String actionNs,
      @RequestParam(value = "remotingApiVar", required = false, defaultValue = "REMOTING_API") final String remotingApiVar,
      @RequestParam(value = "pollingUrlsVar", required = false, defaultValue = "POLLING_URLS") final String pollingUrlsVar,
      @RequestParam(value = "group", required = false) final String group, HttpServletRequest request) {

    //response.setContentType("application/x-javascript");

    String requestUrlString = request.getRequestURL().toString();
    boolean debug = requestUrlString.contains("api-debug.js");

    ApiCacheKey apiKey = new ApiCacheKey(apiNs, actionNs, remotingApiVar, pollingUrlsVar, group, debug);
    String apiString = ApiCache.INSTANCE.get(apiKey);
    if (apiString == null) {

      String routerUrl;
      String basePollUrl;

      if (!debug) {
        routerUrl = requestUrlString.replace("api.js", "router");
        basePollUrl = requestUrlString.replace("api.js", "poll");
      } else {
        routerUrl = requestUrlString.replace("api-debug.js", "router");
        basePollUrl = requestUrlString.replace("api-debug.js", "poll");
      }
      apiString = buildApiString(apiNs, actionNs, remotingApiVar, pollingUrlsVar, routerUrl, basePollUrl, group, debug);
      ApiCache.INSTANCE.put(apiKey, apiString);
    }

    return apiString;

  }

  private String buildApiString(final String apiNs, final String actionNs, final String remotingApiVar, final String pollingUrlsVar,
      final String routerUrl, final String basePollUrl, final String group, final boolean debug) {

    RemotingApi remotingApi = new RemotingApi(routerUrl, actionNs);
    scanForExtDirectAnnotations(remotingApi, group);

    StringBuilder sb = new StringBuilder();

    sb.append("Ext.ns('");
    sb.append(apiNs);
    sb.append("');");

    if (debug) {
      sb.append("\n\n");
    }

    if (actionNs != null && !actionNs.trim().isEmpty()) {
      sb.append("Ext.ns('");
      sb.append(actionNs);
      sb.append("');");

      if (debug) {
        sb.append("\n\n");
      }
    }

    String jsonConfig = ExtDirectSpringUtil.serializeObjectToJson(remotingApi, debug);

    sb.append(apiNs).append(".").append(remotingApiVar).append(" = ");
    sb.append(jsonConfig);
    sb.append(";");

    if (debug) {
      sb.append("\n\n");
    }

    List<PollingProvider> pollingProviders = remotingApi.getPollingProviders();
    if (!pollingProviders.isEmpty()) {

      sb.append(apiNs).append(".").append(pollingUrlsVar).append(" = {");
      if (debug) {
        sb.append("\n");
      }

      for (int i = 0; i < pollingProviders.size(); i++) {
        if (debug) {
          sb.append("  ");
        }

        sb.append(pollingProviders.get(i).getEvent());
        sb.append(" : '").append(basePollUrl).append("/");
        sb.append(pollingProviders.get(i).getBeanName());
        sb.append("/");
        sb.append(pollingProviders.get(i).getMethod());
        sb.append("/");
        sb.append(pollingProviders.get(i).getEvent());
        sb.append("'");
        if (i < pollingProviders.size() - 1) {
          sb.append(",");
          if (debug) {
            sb.append("\n");
          }
        }
      }
      if (debug) {
        sb.append("\n");
      }
      sb.append("};");
    }

    return sb.toString();
  }

  private void scanForExtDirectAnnotations(RemotingApi remotingApi, String group) {
    Map<String, Object> beanDefinitions = getAllBeanDefinitions();

    for (String beanName : beanDefinitions.keySet()) {
      Object bean = beanDefinitions.get(beanName);

      Method[] methods = bean.getClass().getMethods();

      for (Method method : methods) {
        if (AnnotationUtils.findAnnotation(method, ExtDirectMethod.class) != null) {
          if (isSameGroup(group, AnnotationUtils.findAnnotation(method, ExtDirectMethod.class).group())) {
            if (isFormHandlerMethod(method)) {
              remotingApi.addAction(beanName, method.getName(), 0, true);
            } else {
              addAction(remotingApi, beanName, method);
            }
          }
        } else if (AnnotationUtils.findAnnotation(method, ExtDirectPollMethod.class) != null) {
          ExtDirectPollMethod annotation = AnnotationUtils.findAnnotation(method, ExtDirectPollMethod.class);
          if (isSameGroup(group, annotation.group())) {
            String event = annotation.event();
            remotingApi.addPollingProvider(beanName, method.getName(), event);
          }
        } else if (AnnotationUtils.findAnnotation(method, ExtDirectStoreModifyMethod.class) != null) {
          if (isSameGroup(group, AnnotationUtils.findAnnotation(method, ExtDirectStoreModifyMethod.class).group())) {
            addAction(remotingApi, beanName, ExtDirectSpringUtil.findMethodWithAnnotation(method, ExtDirectStoreModifyMethod.class));
          }
        } else if (AnnotationUtils.findAnnotation(method, ExtDirectStoreReadMethod.class) != null) {
          if (isSameGroup(group, AnnotationUtils.findAnnotation(method, ExtDirectStoreReadMethod.class).group())) {
            addAction(remotingApi, beanName, ExtDirectSpringUtil.findMethodWithAnnotation(method, ExtDirectStoreReadMethod.class));
          }
        }
      }

    }
  }

  private boolean isSameGroup(String requestedGroup, String annotationGroup) {
    if (requestedGroup != null) {
      return ExtDirectSpringUtil.equal(requestedGroup, annotationGroup);
    }

    return true;
  }

  private boolean isFormHandlerMethod(Method method) {
    if (AnnotationUtils.findAnnotation(method, RequestMapping.class) != null
        && AnnotationUtils.findAnnotation(method, ResponseBody.class) != null) {
      RequestMethod[] requestMethods = AnnotationUtils.findAnnotation(method, RequestMapping.class).method();
      if (requestMethods != null && requestMethods.length > 0) {
        if (requestMethods[0].equals(RequestMethod.POST)) {
          return true;
        }
      }
    }
    return false;
  }

  private void addAction(RemotingApi remotingApi, String beanName, Method method) {
    Class< ? >[] parameterTypes = method.getParameterTypes();
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    
    int paramLength = 0;
    for (int i = 0; i < parameterTypes.length; i++) {
      if (!SupportedParameters.isSupported(parameterTypes[i]) &&
          !ExtDirectSpringUtil.containsAnnotation(parameterAnnotations[i], RequestParam.class)) {        
        paramLength++;
      }
    }
    remotingApi.addAction(beanName, method.getName(), paramLength, null);
  }

  private Map<String, Object> getAllBeanDefinitions() {
    Map<String, Object> beanDefinitions = new HashMap<String, Object>();

    ApplicationContext currentCtx = context;
    do {

      for (String beanName : currentCtx.getBeanDefinitionNames()) {
        beanDefinitions.put(beanName, currentCtx.getBean(beanName));
      }
      currentCtx = currentCtx.getParent();

    } while (currentCtx != null);

    return beanDefinitions;
  }

}