package io.shiftleft.tarpit;

import io.shiftleft.tarpit.model.User;
import io.shiftleft.tarpit.DocumentTarpit;
import java.io.IOException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;


@WebServlet(name = "simpleServlet", urlPatterns = {"/vulns"}, loadOnStartup = 1)
public class ServletTarPit extends HttpServlet {

  private static final long serialVersionUID = -3462096228274971485L;
  private Connection connection;
  private PreparedStatement preparedStatement;
  private ResultSet resultSet;


  private final static Logger LOGGER = Logger.getLogger(ServletTarPit.class.getName());

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    String ACCESS_KEY_ID = System.getenv("ACCESS_KEY_ID");
    String SECRET_KEY = System.getenv("SECRET_KEY");

    String txns_dir = System.getProperty("transactions_folder","/rolling/transactions");

    String login = request.getParameter("login");
    String password = request.getParameter("password");
    String encodedPath = request.getParameter("encodedPath");

    String xxeDocumentContent = request.getParameter("entityDocument");
    DocumentTarpit.getDocument(xxeDocumentContent);

    boolean keepOnline = (request.getParameter("keeponline") != null);

    LOGGER.info(" Transactions Folder is " + txns_dir);

    try {
      // upgrade to AES from deprecated DES
      Cipher aes = Cipher.getInstance("AES");
      SecretKey key = KeyGenerator.getInstance("AES").generateKey();
      aes.init(Cipher.ENCRYPT_MODE, key);

      getConnection();

      // Use parameterized query instead of concatanation to prevent SQL injection
      String sql = "SELECT * FROM USER WHERE LOGIN = ? AND PASSWORD = ?";
      preparedStatement = connection.prepareStatement(sql);
      preparedStatement.setString(1, login);
      preparedStatement.setString(2, password);

      resultSet = preparedStatement.executeQuery();

      if (resultSet.next()) {

        User user = new User(login,
            resultSet.getString("fname"),
            resultSet.getString("lname"),
            resultSet.getString("passportnum"),
            resultSet.getString("address1"),
            resultSet.getString("address2"),
            resultSet.getString("zipCode"));

        String creditInfo = resultSet.getString("userCreditCardInfo");
        byte[] cc_enc_str = aes.doFinal(creditInfo.getBytes());

        Cookie cookie = new Cookie("login", login);
        cookie.setMaxAge(864000);
        cookie.setPath("/");
        // Set HttpOnly and secure flag to cookies
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        response.addCookie(cookie);

        request.setAttribute("user", user.toString());
        request.setAttribute("login", login);

        LOGGER.info(" User successfully logged in ");

        getServletContext().getRequestDispatcher("/dashboard.jsp").forward(request, response);

      } else {
        request.setAttribute("login", login);
        request.setAttribute("password", password);
        request.setAttribute("keepOnline", keepOnline);
        request.setAttribute("message", "Failed to Sign in. Please verify credentials");

        LOGGER.info(" UserId failed to logged in ");

        getServletContext().getRequestDispatcher("/signIn.jsp").forward(request, response);
      }
    } catch (Exception e) {
      throw new ServletException(e);
    }

  }

  private void getConnection() throws ClassNotFoundException, SQLException {
    Class.forName("com.mysql.jdbc.Driver");
    connection = DriverManager.getConnection("jdbc:mysql://localhost/DBPROD", "admin", "1234");
  }

}
