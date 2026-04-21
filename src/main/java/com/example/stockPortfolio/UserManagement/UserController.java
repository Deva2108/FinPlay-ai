package com.example.stockPortfolio.UserManagement;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.stockPortfolio.ExceptionManagement.ErrorClass;
import com.example.stockPortfolio.ExceptionManagement.UserNotFoundException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/user")
@Tag(name="1. User", description = "1st Controller")
public class UserController {

    //injecting the service class, methods inside this
    //we call it dependency injection
    @Autowired
    UserService userService;

    //api for register
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserModel userModel){
        System.out.println("REGISTER PAYLOAD: " + userModel);
        try {
            UserModel saved = userService.register(userModel);
            return ResponseEntity.ok(saved);
        } catch (UserNotFoundException e) {
            System.err.println("REGISTER ERROR (User Exists): " + e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorClass(HttpStatus.CONFLICT.value(), e.getMessage(), LocalDateTime.now()));
        } catch (Exception e) {
            System.err.println("REGISTER ERROR (Internal): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorClass(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error: " + e.getMessage(), LocalDateTime.now()));
        }
    }

    //api for login
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO loginRequest){
        System.out.println("LOGIN PAYLOAD: " + loginRequest);
        try {
            String token = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
            UserModel user = userService.getUserByEmail(loginRequest.getEmail());
            
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("token", token);
            response.put("user", user);
            
            return ResponseEntity.ok(response);
        } catch (UserNotFoundException e) {
            System.err.println("LOGIN ERROR (Auth): " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorClass(HttpStatus.UNAUTHORIZED.value(), e.getMessage(), LocalDateTime.now()));
        } catch (Exception e) {
            System.err.println("LOGIN ERROR (Internal): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorClass(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", LocalDateTime.now()));
        }
    }

    //api for updating the profile
    @PutMapping("/updateProfile/{email}")
    public UserModel update(@Valid @PathVariable String email,@Valid @RequestBody UserModel userModel){
        return userService.editProfile(email, userModel);
    }
}
