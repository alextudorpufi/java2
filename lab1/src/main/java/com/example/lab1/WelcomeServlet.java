package com.example.lab1;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("")
public class WelcomeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws IOException {

        response.setContentType("text/html");

        response.getWriter().println("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>Lab 1</title>
            </head>
            <body>
                <h1>Welcome to Lab 1</h1>

                <form action="controller" method="GET">
                    <label>Select a page:</label>

                    <select name="page">
                        <option value="1">Page 1</option>
                        <option value="2">Page 2</option>
                    </select>

                    <button type="submit">Go</button>
                </form>
            </body>
            </html>
            """);
    }
}