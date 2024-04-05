package io.shiftleft.tarpit;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.shiftleft.tarpit.model.Order;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import io.shiftleft.tarpit.util.EmailService;

@WebServlet(name = "simpleServlet", urlPatterns = { "/processOrder" }, loadOnStartup = 1)
public class OrderProcessor extends HttpServlet {

  private static ObjectMapper deserializer = new ObjectMapper().enableDefaultTyping();
  private static ObjectMapper serializer = new ObjectMapper();

  private EmailService emailService = new EmailService(System.getenv("EMAIL_HOST"), Integer.parseInt(System.getenv("MAIL_PORT")), System.getenv("MAIL_USER"), System.getenv("MAIL_PASS"));
  private String fromAddress = System.getenv("FROM_MAIL_ADDRESS");

  private Connection connection;
  private PreparedStatement preparedStatement;
  private ResultSet resultSet;


  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    try {
      Order customerOrder = Order.createOrder();
      out.println(org.jsoup.Jsoup.parse(serializer.writeValueAsString(customerOrder)).text());

      getConnection();

      Statement statement = connection.createStatement();
      statement.executeUpdate("INSERT INTO Order " +
          "VALUES ('1234','5678', '04/10/2019', 'PENDING', '04/10/2019', 'Lakeside Drive', 'Santa Clara', 'CA', '95054', 'mike@waltz.com')");

      String customerEmail = customerOrder.getEmailAddress();
      String subject = "Transactions Status of Order : " + customerOrder.getOrderId();
      String verifyUri = fromAddress + "/order/" + customerOrder.getOrderId();
      String message = " Your Order was successfully processed. For Order status please verify on page : " +  verifyUri;
      emailService.sendMail(fromAddress, customerEmail, subject, org.jsoup.Jsoup.parse(message).text());

    } catch (JsonGenerationException e) {
    } catch (JsonMappingException e) {
    } catch (IOException e) {
    } catch (ParseException e) {
    } catch (Exception e) {
    }
    out.close();
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/html");
    PrintWriter out = response.getWriter();

    try {
      Order order = deserializer.readValue(request.getReader(), Order.class);
      out.println(org.jsoup.Jsoup.parse(order.toString()).text());
    } catch (JsonGenerationException e) {
    } catch (JsonMappingException e) {
    } catch (IOException e) {
    }
    out.close();
  }

  private void getConnection() throws ClassNotFoundException, SQLException {
    Class.forName("com.mysql.jdbc.Driver");
    connection = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASS"));
  }

}
