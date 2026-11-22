package dev.subtrack.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
class ProblemErrorController implements ErrorController {

  @RequestMapping("${server.error.path:/error}")
  ResponseEntity<Object> error(HttpServletRequest request) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    ProblemType type =
        switch (status instanceof Integer code ? code : 500) {
          case 400 -> ProblemType.MALFORMED_REQUEST;
          case 401 -> ProblemType.UNAUTHORIZED;
          case 404 -> ProblemType.NOT_FOUND;
          case 405 -> ProblemType.METHOD_NOT_ALLOWED;
          case 415 -> ProblemType.UNSUPPORTED_MEDIA_TYPE;
          default -> ProblemType.INTERNAL_ERROR;
        };
    return Problems.response(type);
  }
}
