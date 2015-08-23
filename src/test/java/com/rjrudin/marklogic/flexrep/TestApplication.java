package com.rjrudin.marklogic.flexrep;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Uses Spring Boot to fire up a Tomcat instance that can proxy HTTP requests from the master ML.
 */
@Controller
@EnableAutoConfiguration(exclude = { org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration.class })
public class TestApplication {

    private FlexrepFileProxy flexrepProxy = new FlexrepFileProxy();

    public static void main(String[] args) throws Exception {
        SpringApplication.run(TestApplication.class, args);
    }

    @RequestMapping("/apply.xqy")
    @ResponseBody
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws Exception {
        flexrepProxy.proxy(request, response);
    }

}