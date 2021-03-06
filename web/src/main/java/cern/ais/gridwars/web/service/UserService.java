package cern.ais.gridwars.web.service;

import cern.ais.gridwars.web.config.GridWarsProperties;
import cern.ais.gridwars.web.config.oauth.OAuthAuthentication;
import cern.ais.gridwars.web.config.oauth.OAuthorizedToken;
import cern.ais.gridwars.web.controller.user.NewUserDto;
import cern.ais.gridwars.web.controller.user.OAuthToken;
import cern.ais.gridwars.web.controller.user.UpdateUserDto;
import cern.ais.gridwars.web.domain.User;
import cern.ais.gridwars.web.repository.UserRepository;
import cern.ais.gridwars.web.util.DomainUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;


@Service
public class UserService implements UserDetailsService {

    private final transient GridWarsProperties gridWarsProperties;
    private final transient UserRepository userRepository;
    private final transient UserMailingService userMailingService;
    private final transient RestTemplate restTemplateOAuth;

    @Autowired
    public UserService(final GridWarsProperties gridWarsProperties,
                       final UserRepository userRepository,
                       final UserMailingService userMailingService,
                       final RestTemplate restTemplateOAuth) {
        this.gridWarsProperties = Objects.requireNonNull(gridWarsProperties);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.userMailingService = Objects.requireNonNull(userMailingService);
        this.restTemplateOAuth = Objects.requireNonNull(restTemplateOAuth);
    }

    public OAuthToken getUserOAuthToken(final String code) {
        Map<String, String> variables = new HashMap<>();
        variables.put("grant_type", gridWarsProperties.getOAuth().getGrantType());
        variables.put("code", code);
        return restTemplateOAuth.getForObject(gridWarsProperties.getOAuth().getTokenUrl(), OAuthToken.class, variables);
    }

    public OAuthorizedToken validateUserToken(final String token) throws RestClientException {
        Map<String, String> variables = new HashMap<>();
        variables.put("token", token);
        return restTemplateOAuth.getForObject(gridWarsProperties.getOAuth().getCheckTokenUrl(), OAuthorizedToken.class,
            variables);
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        return userRepository
            .findByUsernameIgnoreCase(username)
            .map(this::populateAuthorities)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public Optional<User> getById(String userId) {
        return userRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public boolean isExistingUser(final String userId) {
        return userRepository.existsByUsernameIgnoreCase(userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> getByConfirmationId(String confirmationId) {
        return userRepository.findByConfirmationId(confirmationId);
    }

    @Transactional(readOnly = true)
    public boolean hasExistingUsers() {
        return userRepository.count() > 0;
    }

    @Transactional(readOnly = true)
    public List<User> getAllNonAdminUsers() {
        return userRepository.findAllByAdminIsFalse();
    }

    @Transactional
    public void confirmUser(String userId) {
        getById(userId).ifPresent(user -> {
            user.setConfirmed(Instant.now());
            userRepository.saveAndFlush(user);
        });
    }

    private User populateAuthorities(final User user) {
        user.clearAuthorities();
        user.addAuthority(User.ROLE_USER_AUTHORITY);

        if (user.isAdmin()) {
            user.addAuthority(User.ROLE_ADMIN_AUTHORITY);
        }

        return user;
    }

    @Transactional
    public User create(NewUserDto newUserDto, boolean createAdmin, boolean bypassConfirmation, boolean sendMailToAdmin) {
        if (userRepository.existsByUsernameIgnoreCase(newUserDto.getUsername())) {
            throw new UserFieldValueException("username", "user.error.exists.username");
        }

        User newUser = saveNewUser(newUserDto, createAdmin, bypassConfirmation);

        if (!bypassConfirmation) {
            userMailingService.sendConfirmationMail(newUser);
        }

        if (sendMailToAdmin) {
            userMailingService.sendUserRegistrationMailToAdmin(newUser);
        }

        return newUser;
    }

    private User saveNewUser(NewUserDto newUserDto, boolean createAdmin, boolean bypassConfirmation) {
        User newUser = new User()
            .setId(newUserDto.getUsername())
            .setUsername(newUserDto.getUsername())
            .setEmail(newUserDto.getEmail())
            .setCreated(Instant.now())
            .setTeamName(newUserDto.getTeamName())
            .setAdmin(createAdmin)
            .setConfirmationId(DomainUtils.generateId())
            .setIp(newUserDto.getIp())
            .setEnabled(true);

        if (bypassConfirmation) {
            newUser.setConfirmed(Instant.now());
        } else {
            newUser.setConfirmed(null);
        }

        userRepository.saveAndFlush(newUser);
        return newUser;
    }

    @Transactional
    public void update(UpdateUserDto updateUserDto) {
        if (!userRepository.existsById(updateUserDto.getId())) {
            throw new RuntimeException("User does not exist: " + updateUserDto.getId() + " (" + updateUserDto.getUsername() + ")");
        }

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(updateUserDto.getEmail(), updateUserDto.getId())) {
            throw new UserFieldValueException("email", "user.error.exists.email");
        }

        updateExistingUser(updateUserDto);
    }

    private void updateExistingUser(UpdateUserDto updateUserDto) {
       userRepository.findById(updateUserDto.getId()).ifPresent(existingUser -> {
           existingUser.setEmail(updateUserDto.getEmail());
           existingUser.setTeamName(updateUserDto.getTeamName());
           existingUser.touch();

           userRepository.saveAndFlush(existingUser);
       });
    }

    public void destroyAuthenticationToken() {
        OAuthAuthentication oAuth = (OAuthAuthentication) SecurityContextHolder.getContext().getAuthentication();
        Map<String, String> variables = new HashMap<>();
        variables.put("token", (String) oAuth.getCredentials());
        restTemplateOAuth.delete(gridWarsProperties.getOAuth().getRevokeTokenUrl(), variables);
    }

    public static class UserFieldValueException extends RuntimeException {

        private final String fieldName;
        private final String errorMessageCode;

        UserFieldValueException(String fieldName, String errorMessageCode) {
            this.fieldName = fieldName;
            this.errorMessageCode = errorMessageCode;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getErrorMessageCode() {
            return errorMessageCode;
        }
    }
}
