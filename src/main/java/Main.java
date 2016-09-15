import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.Issue;
import org.apache.commons.codec.binary.Base64;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Main {

    private static String userName;
    private static String password;
    private static byte[] basicAuth;
    private static Timer timer = new Timer();
    private static SystemTray systemTray;
    private static TrayIcon trayIcon;
    private static BufferedImage neutralImage;
    private static BufferedImage alertImage;
    private static long cnt = 0;

    public static void main(String[] args) {
        if (SystemTray.isSupported()) {
            try {
                loginAndMoveToTray();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "PR Notificator", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(null, "System tray is not supported on this system", "PR Notificator", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void loginAndMoveToTray() throws IOException {
        login();

        scheduleChecking();

        activateTray();
    }

    private static void login() {
        JTextField userNameField = new JTextField();
        JTextField passwordField = new JPasswordField();
        Object[] message = {
                "Username:", userNameField,
                "Password:", passwordField
        };

        int option = JOptionPane.showConfirmDialog(null, message, "PR Notificator - Github Login", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            userName = userNameField.getText();
            password = passwordField.getText();

            basicAuth = Base64.encodeBase64((userName + ":" + password).getBytes());
        } else {
            System.exit(0);
        }
    }

    private static void scheduleChecking() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long prCount = getNumberOfAssignedPullRequests();

                if (prCount == 0) {
                    trayIcon.setImage(neutralImage);
                    trayIcon.setToolTip("Nothing new. Last check " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                } else {
                    trayIcon.setImage(alertImage);
                    trayIcon.setToolTip("You have " + prCount + " pull requests.");
                    trayIcon.displayMessage("You have " + prCount + " pull requests.", null, TrayIcon.MessageType.INFO);
                }
            }
        }, 5000, 10000);
    }

    private static void activateTray() throws IOException {
        neutralImage = ImageIO.read(Main.class.getClassLoader().getResource("images/icon_pullrequest.png"));
        alertImage = ImageIO.read(Main.class.getClassLoader().getResource("images/git-pull-request-256-red.png"));

        trayIcon = new TrayIcon(neutralImage);
        trayIcon.setImageAutoSize(true);

        PopupMenu popup = new PopupMenu();
        MenuItem exitItem = new MenuItem("Exit");

        exitItem.addActionListener(e -> {
            systemTray.remove(trayIcon);
            timer.cancel();
            System.exit(0);
        });

        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);

        systemTray = SystemTray.getSystemTray();
        try {
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    private static long getNumberOfAssignedPullRequests() {
        Client client = ClientBuilder.newClient();

        URI uri = UriBuilder
                .fromUri("https://api.github.com").path("issues")
                .build();

        Invocation.Builder builder = client.target(uri).request();
        builder.header("Content-Type", "application/json");
        //builder.header("Authorization", "Basic UGVldGVlQ1o6WmRyb2phYWt5MQ==");
        builder.header("Authorization", "Basic " + new String(basicAuth));

        Response response = builder.get();
        if (response.getStatusInfo().getStatusCode() == Response.Status.OK.getStatusCode()) {
            String responseJson = response.readEntity(String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            try {
                List<Issue> issues = objectMapper.readValue(responseJson, new TypeReference<List<Issue>>() {});
                return issues.size();
            } catch (IOException e) {
                return 0;
            }
        }

        return 0;
    }
}
