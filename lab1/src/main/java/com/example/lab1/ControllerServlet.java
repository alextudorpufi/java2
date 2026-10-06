package com.example.lab1;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;

@WebServlet("/controller")
public class ControllerServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        handleRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        handleRequest(request, response);
    }

    private void handleRequest(HttpServletRequest request,
                               HttpServletResponse response)
            throws ServletException, IOException {

        String page = request.getParameter("page");

        // Required server logging
        System.out.println("----- New Request -----");
        System.out.println("Method: " + request.getMethod());
        System.out.println("Client IP: " + request.getRemoteAddr());
        System.out.println("User-Agent: " + request.getHeader("User-Agent"));

        System.out.println("Languages:");
        Collections.list(request.getLocales())
                .forEach(locale ->
                        System.out.println(" - " + locale));

        System.out.println("Parameter page: " + page);

        // Desktop application requests plain text
        String accept = request.getHeader("Accept");

        if (accept != null && accept.contains("text/plain")) {
            response.setContentType("text/plain");
            response.getWriter().println(page);
            return;
        }

        // Browser request -> forward to selected page
        if ("1".equals(page)) {
            request.getRequestDispatcher("/page1.html")
                    .forward(request, response);
        } else if ("2".equals(page)) {
            request.getRequestDispatcher("/page2.html")
                    .forward(request, response);
        } else {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid page parameter"
            );
        }
    }
}