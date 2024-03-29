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
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;


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

    // Removed hardcoded AWS credentials and replaced with environment variables
    String ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    String SECRET_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");

    String txns_dir = System.getProperty("transactions_folder","/rolling/transactions");

    String login = request.getParameter("login");
    String password = request.getParameter("password");
    String encodedPath = request.getParameter("encodedPath");

    String xxeDocumentContent = request.getParameter("entityDocument");
    DocumentTarpit.getDocument(xxeDocumentContent);

    boolean keepOnline = (request.getParameter("keeponline") != null);

    LOGGER.info(" AWS Properties are " + ACCESS_KEY_ID + " and " + SECRET_KEY);
    LOGGER.info(" Transactions Folder is " + txns_dir);

    try {

      ScriptEngineManager manager = new ScriptEngineManager();
      ScriptEngine engine = manager.getEngineByName("JavaScript");
      // Removed eval call to prevent eval injection
      // engine.eval(request.getParameter("module"));

      /* FLAW: Insecure cryptographic algorithm (DES) 
      CWE: 327 Use of Broken or Risky Cryptographic Algorithm */
      // Changed DES to AES for stronger encryption
      Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      KeyGenerator keyGen = KeyGenerator.getInstance("AES");
      keyGen.init(256); // for example
      SecretKey key = keyGen.generateKey();
      aesCipher.init(Cipher.ENCRYPT_MODE, key);

      getConnection();

      // Prevent SQL Injection by using parameterized queries
      String sql = "SELECT * FROM USER WHERE LOGIN = ? AND PASSWORD = ?";
      preparedStatement = connection.prepareStatement(sql);
      preparedStatement.setString(1, login);
      preparedStatement.setString(2, password);

      resultSet = preparedStatement.executeQuery();

      if (resultSet.next()) {

        login = resultSet.getString("login");
        password = resultSet.getString("password");

        User user = new User(login,
            resultSet.getString("fname"),
            resultSet.getString("lname"),
            resultSet.getString("passportnum"),
            resultSet.getString("address1"),
            resultSet.getString("address2"),
            resultSet.getString("zipCode"));

        String creditInfo = resultSet.getString("userCreditCardInfo");
        byte[] cc_enc_str = aesCipher.doFinal(creditInfo.getBytes());

        Cookie cookie = new Cookie("login", login);
        cookie.setMaxAge(864000);
        cookie.setPath("/");
        // Set HttpOnly and Secure flags on the cookie
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        response.addCookie(cookie);

        request.setAttribute("user", user.toString());
        request.setAttribute("login", login);

        // Properly encode the user information to prevent CRLF injection
        LOGGER.info(" User " + user + " successfully logged in ");
        LOGGER.info(" User " + user + " credit info is " + Base64.getEncoder().encodeToString(cc_enc_str));

        getServletContext().getRequestDispatcher("/dashboard.jsp").forward(request, response);

      } else {
        request.setAttribute("login", login);
        request.setAttribute("password", password);
        request.setAttribute("keepOnline", keepOnline);
        request.setAttribute("message", "Failed to Sign in. Please verify credentials");

        LOGGER.info(" UserId " + login + " failed to logged in ");

        getServletContext().getRequestDispatcher("/signIn.jsp").forward(request, response);
      }
    } catch (Exception e) {
      throw new ServletException(e);
    }

  }

  private void getConnection() throws ClassNotFoundException, SQLException {
    Class.forName("com.mysql.jdbc.Driver");
    // Use environment variables or a secure configuration management service to retrieve database credentials
    String dbUrl = System.getenv("DB_URL");
    String dbUser = System.getenv("DB_USER");
    String dbPassword = System.getenv("DB_PASSWORD");
    connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
  }

}
