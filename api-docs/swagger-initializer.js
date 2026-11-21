window.onload = () => {
  window.ui = SwaggerUIBundle({
    url: "/openapi.yaml",
    dom_id: "#swagger-ui",
    layout: "BaseLayout",
    deepLinking: true,
    displayOperationId: true,
    // Otherwise Swagger UI may send the spec to the public validator.swagger.io.
    validatorUrl: null,
  });
};
