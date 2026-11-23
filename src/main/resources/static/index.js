const stripe = Stripe("@stripe.publishable.key@");

initialize();


// Create a Checkout Session
async function initialize() {
  const fetchClientSecret = async () => {
    const response = await fetch("/@endpoint.createPaymentSession@", {
      method: "POST",
    });
    const data = await response.json();
    if (data.error) {
      throw new Error(data.error);
    }
    return data.clientSecret;
  };

  const checkout = await stripe.createEmbeddedCheckoutPage({
    fetchClientSecret,
  });

  // Mount Checkout
  checkout.mount('#checkout');
}