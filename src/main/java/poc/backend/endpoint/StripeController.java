package poc.backend.endpoint;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
public class StripeController {

    private static final Logger log = LoggerFactory.getLogger(StripeController.class);


    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private StripeClient client;


    // Spring IOC is singleton, so this is only called once per lifetime.
    //4242 4242 4242 4242
    //4000 0025 0000 3155
    //4000 0000 0000 9995

    @PostConstruct
    public void init() {
        this.client = new StripeClient(stripeApiKey);
    }

    @PostMapping("/${endpoint.createPaymentSession}")
    public Map<String, String> createPaymentSession(HttpServletRequest request) throws StripeException {
        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null)
                .build()
                .toUriString();




        log.info("baseUrl {}", baseUrl);

        SessionCreateParams params = SessionCreateParams.builder()
                .setUiMode(SessionCreateParams.UiMode.EMBEDDED_PAGE)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setReturnUrl(baseUrl + "/return.html?session_id={CHECKOUT_SESSION_ID}")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("cad")
                                                .setUnitAmount(100L)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData
                                                                .ProductData.builder()
                                                                .setName("Jarvis$")
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        Session session = client.v1().checkout().sessions().create(params);

        Map<String, String> map = new HashMap<>();
        map.put("clientSecret", session.getClientSecret());
        return map;
    }

    @GetMapping("/${endpoint.checkPaymentSessionStatus}")
    public Map<String, String> checkPaymentSessionStatus(@RequestParam("session_id") String sessionId) throws StripeException {
        Session session = client.v1().checkout().sessions().retrieve(sessionId);

        Map<String, String> map = new HashMap<>();
        map.put("status", session.getStatus());
        if (session.getCustomerDetails() != null) {
            map.put("customer_email", session.getCustomerDetails().getEmail());
        }
        return map;
    }

    @PostMapping("/${endpoint.paymentSessionResult}")
    public ResponseEntity<?> paymentSessionResult(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Webhook Error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Webhook Error: " + e.getMessage());
        }

        switch (event.getType()) {
            case "checkout.session.completed": {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new RuntimeException("Deserialization failed"));

                System.out.println("Checkout completed: " + session.getId());
                System.out.println("Payment status: " + session.getPaymentStatus());
                if (session.getCustomerDetails() != null) {
                    System.out.println("Customer email: " + session.getCustomerDetails().getEmail());
                }
                break;
            }
            case "payment_intent.succeeded": {
                PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new RuntimeException("Deserialization failed"));

                System.out.println("PaymentIntent succeeded: " + paymentIntent.getId());
                System.out.println("Amount: " + paymentIntent.getAmount());
                break;
            }
            case "payment_intent.payment_failed": {
                PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new RuntimeException("Deserialization failed"));

                System.out.println("Payment failed: " + paymentIntent.getId());
                if (paymentIntent.getLastPaymentError() != null) {
                    System.out.println("Failure message: " +
                            paymentIntent.getLastPaymentError().getMessage());
                }
                break;
            }
            default:
                System.out.println("Unhandled event type: " + event.getType());
        }

        return ResponseEntity.ok(Collections.emptyMap());
    }
}
