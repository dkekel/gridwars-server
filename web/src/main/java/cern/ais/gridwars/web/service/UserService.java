package cern.ais.gridwars.web.service;

import cern.ais.gridwars.web.controller.NewUserDto;
import cern.ais.gridwars.web.controller.UpdateUserDto;
import cern.ais.gridwars.web.util.DomainUtils;
import cern.ais.gridwars.web.domain.User;
import cern.ais.gridwars.web.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserMailingService userMailingService;


    @Autowired
    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                       UserMailingService userMailingService) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.userMailingService = Objects.requireNonNull(userMailingService);
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        return userRepository
            .findByUsername(username)
            .map(this::populateAuthorities)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public Optional<User> getById(String userId) {
        return userRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> getByConfirmationId(String confirmationId) {
        return userRepository.findByConfirmationId(confirmationId);
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
        if (userRepository.existsByUsername(newUserDto.getUsername())) {
            throw new UserFieldValueException("username", "user.error.exists.username");
        }

        if (userRepository.existsByEmail(newUserDto.getEmail())) {
            throw new UserFieldValueException("email", "user.error.exists.email");
        }

        if (userRepository.existsByTeamName(newUserDto.getTeamName())) {
            throw new UserFieldValueException("teamName", "user.error.exists.teamName");
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
            .setId(DomainUtils.generateId())
            .setUsername(newUserDto.getUsername())
            .setPassword(encodePassword(newUserDto.getPassword()))
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

        if (userRepository.existsByUsernameAndIdNot(updateUserDto.getUsername(), updateUserDto.getId())) {
            throw new UserFieldValueException("username", "user.error.exists.username");
        }

        if (userRepository.existsByEmailAndIdNot(updateUserDto.getEmail(), updateUserDto.getId())) {
            throw new UserFieldValueException("email", "user.error.exists.email");
        }

        if (userRepository.existsByTeamNameAndIdNot(updateUserDto.getTeamName(), updateUserDto.getId())) {
            throw new UserFieldValueException("teamName", "user.error.exists.teamName");
        }

        updateExistingUser(updateUserDto);
    }

    private void updateExistingUser(UpdateUserDto updateUserDto) {
       userRepository.findById(updateUserDto.getId()).ifPresent(existingUser -> {
           existingUser.setEmail(updateUserDto.getEmail());
           existingUser.setTeamName(updateUserDto.getTeamName());
           existingUser.touch();

           if (StringUtils.hasLength(updateUserDto.getPassword())) {
               existingUser.setPassword(encodePassword(updateUserDto.getPassword()));
           }

           userRepository.saveAndFlush(existingUser);
       });
    }

    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    @Transactional
    public void changeUserPassword(String userId, String newPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setPassword(encodePassword(newPassword));
            user.touch();
            userRepository.saveAndFlush(user);
        });
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
