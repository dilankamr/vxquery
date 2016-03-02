package org.apache.vxquery.webui.controller;

import org.apache.vxquery.webui.model.VXQuery;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/")
public class ViewController {

    @RequestMapping(method = RequestMethod.GET)
    public String sayHello(ModelMap model) {
        return "welcome";
    }

    @RequestMapping(value = "/executeQuery", method = RequestMethod.POST)
    public ResponseEntity<String> sayHelloAgain(@RequestBody String query) throws CmdLineException {

        VXQuery vxq = new VXQuery();
        String result;

        try {
            result = vxq.run(query);
        } catch (Exception e) {
            result = e.toString();
            System.out.println("****************************************************");
            System.out.println(result);
            System.out.println("****************************************************");
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }


        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}

