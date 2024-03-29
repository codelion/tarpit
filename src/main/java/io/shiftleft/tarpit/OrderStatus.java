package io.shiftleft.tarpit;

import io.shiftleft.tarpit.model.Order;
import io.shiftleft.tarpit.model.User;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = "simpleServlet", urlPatterns = {"/getOrderStatus"}, loadOnStartup = 1)
public class OrderStatus extends HttpServlet {

  private static final long serialVersionUID = -3462096228274971485L;
  private Connection connection;
  private PreparedStatement preparedStatement;
  private ResultSet resultSet;

  private final static Logger LOGGER = Logger.getLogger(ServletTarPit.class.getName());

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    String orderId = sanitize(request.getParameter("orderId"));

    boolean keepOnline = (request.getParameter("keeponline") != null);

    try {

      String theUser = sanitize(request.getParameter("userId"));
      String thePassword = sanitize(request.getParameter("password"));
      request.setAttribute("callback", "/orderStatus.jsp");

      getServletContext().getRequestDispatcher("/login.jsp").forward(request, response);

      boolean loggedIn = request.isUserInRole("customer");

      if (loggedIn) {

        getConnection();

        String sql = "SELECT * FROM ORDER WHERE ORDERID = ?";
        preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, orderId);

        resultSet = preparedStatement.executeQuery();

        if (resultSet.next()) {

          orderId = resultSet.getString("login");

          Order order = new Order(orderId,
              resultSet.getString("custId"),
              resultSet.getDate("orderDate"),
              resultSet.getString("orderStatus"),
              resultSet.getDate("shipDate"),
              resultSet.getString("creditCardNumber"),
              resultSet.getString("street"),
              resultSet.getString("city"),
              resultSet.getString("state"),
              resultSet.getString("zipCode"),
              resultSet.getString("emailAddress"));

          Cookie cookie = new Cookie("order", orderId);
          cookie.setMaxAge(864000);
          cookie.setPath("/");
          cookie.setHttpOnly(true);
          cookie.setSecure(true);
          response.addCookie(cookie);

          request.setAttribute("orderDetails", order);

          LOGGER.info("Order details are " + sanitize(order.toString()));

          getServletContext().getRequestDispatcher("/dashboard.jsp").forward(request, response);

        } else {

          request.setAttribute("message", "Order does not exist");

          LOGGER.info(" Order " + sanitize(orderId) + " does not exist ");

          getServletContext().getRequestDispatcher("/error.jsp").forward(request, response);
        }

      } else {

        getServletContext().getRequestDispatcher("/login.jsp").forward(request, response);
      }

    } catch (Exception e) {
      throw new ServletException(e);
    }


  }

  private void getConnection() throws ClassNotFoundException, SQLException {
    Class.forName("com.mysql.jdbc.Driver");
    // Use environment variables or a secure configuration management service to retrieve credentials
    String username = System.getenv("DB_USERNAME");
    String password = System.getenv("DB_PASSWORD");
    connection = DriverManager.getConnection("jdbc:mysql://localhost/DBPROD", username, password);
  }

  private String sanitize(String input) {
    return input.replaceAll("(\r\n|\r|\n|\n\r)", "");
  }
}
