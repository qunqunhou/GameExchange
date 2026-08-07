package com.game.monitor;


import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import java.io.IOException;


@WebServlet("/metrics")
public class MetricsServlet extends HttpServlet {


    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {


        response.setContentType(
                "text/plain; version=0.0.4"
        );


        response.getWriter()
                .write(
                        MetricsConfig
                                .getRegistry()
                                .scrape()
                );
    }
}