package com.example.stockPortfolio.UserManagement;

import com.example.stockPortfolio.ExceptionManagement.UserNotFoundException;
import com.example.stockPortfolio.PortfolioManagement.Portfolio;
import com.example.stockPortfolio.PortfolioManagement.PortfolioService;
import com.example.stockPortfolio.Security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PortfolioService portfolioService;

    @Transactional
    public UserModel register(UserModel userModel){
        if(userRepo.existsByEmail(userModel.getEmail())){
            throw new UserNotFoundException("User already exists in our database");
        }else{
            userModel.setPassword(passwordEncoder.encode(userModel.getPassword()));
            UserModel savedUser = userRepo.save(userModel);
            
            // Auto-create initial portfolio for UX
            Portfolio defaultPortfolio = new Portfolio();
            defaultPortfolio.setUserId(savedUser.getUserId());
            defaultPortfolio.setPortfolioName("My Learning Portfolio 🎒");
            portfolioService.addPortfolio(defaultPortfolio);
            
            return savedUser;
        }
    }

    public String login(String email, String password){
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (Exception e) {
            throw new UserNotFoundException("Incorrect email or password!");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        return jwtUtils.generateToken(userDetails);
    }

    public UserModel getUserByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    public UserModel editProfile(String email, UserModel userModel){
        return userRepo.findByEmail(email)
                .map(existed->{
                    existed.setName(userModel.getName());
                    existed.setEmail(userModel.getEmail());
                    if (userModel.getPassword() != null && !userModel.getPassword().isEmpty()) {
                        existed.setPassword(passwordEncoder.encode(userModel.getPassword()));
                    }
                    return userRepo.save(existed);
                }).orElseThrow(()->new UserNotFoundException("Email not found: "+email));
    }
}
