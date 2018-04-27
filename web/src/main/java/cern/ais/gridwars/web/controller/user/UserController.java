package cern.ais.gridwars.web.controller.user;

import cern.ais.gridwars.web.config.GridWarsProperties;
import cern.ais.gridwars.web.controller.error.AccessDeniedException;
import cern.ais.gridwars.web.controller.error.NotFoundException;
import cern.ais.gridwars.web.domain.User;
import cern.ais.gridwars.web.service.UserService;
import cern.ais.gridwars.web.util.ModelAndViewBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Objects;


@Controller
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final GridWarsProperties gridWarsProperties;

    @Autowired
    public UserController(UserService userService, GridWarsProperties gridWarsProperties) {
        this.userService = Objects.requireNonNull(userService);
        this.gridWarsProperties = Objects.requireNonNull(gridWarsProperties);
    }

    // IMPORTANT: Only map GET method here, not POST, to let the login filter do its work.
    @GetMapping("/signin")
    public ModelAndView showSignin() {
        return ModelAndViewBuilder.forPage("user/signin").toModelAndView();
    }

    @GetMapping("/signup")
    public ModelAndView showSignup(@AuthenticationPrincipal User currentUser) {
        restrictAccessForSignedInNonAdminUser(currentUser);

        if (isUserRegistrationDisabled() && !isAdmin(currentUser)) {
            return ModelAndViewBuilder.forPage("user/signupDisabled").toModelAndView();
        } else {
            return ModelAndViewBuilder.forPage("user/signup")
                .addAttribute("newUser", new NewUserDto())
                .toModelAndView();
        }
    }

    private void restrictAccessForSignedInNonAdminUser(User currentUser) {
        if ((currentUser != null) && !isAdmin(currentUser)) {
            throw new AccessDeniedException();
        }
    }

    private boolean isAdmin(User user) {
        return (user != null) && user.isAdmin();
    }

    @PostMapping("/signup")
    public ModelAndView doSignup(@ModelAttribute("newUser") @Valid NewUserDto newUserDto, BindingResult result,
                                 RedirectAttributes redirectAttributes, HttpServletRequest request,
                                 @AuthenticationPrincipal User currentUser) {
        restrictAccessForSignedInNonAdminUser(currentUser);

        if (isUserRegistrationDisabled() && !isAdmin(currentUser)) {
            throw new AccessDeniedException();
        }

        // Admins can bypass the registration password
        if (!result.hasErrors() && !isAdmin(currentUser)) {
            if (!hasProvidedValidRegistrationPassword(newUserDto)) {
                result.rejectValue("registrationPassword", "user.error.wrong.registrationPassword");
            }
        }

        if (!result.hasErrors()) {
            preprocessNewUser(newUserDto, request);

            try {
                boolean bypassConfirmation = isAdmin(currentUser);
                userService.create(newUserDto, false, bypassConfirmation, true);
            } catch (UserService.UserFieldValueException ufve) {
                result.rejectValue(ufve.getFieldName(), ufve.getErrorMessageCode());
            }
        }

        if (result.hasErrors()) {
            return ModelAndViewBuilder.forPage("user/signup")
                .addAttribute("newUser", newUserDto)
                .toModelAndView();
        } else {
            redirectAttributes.addFlashAttribute("created", newUserDto.getUsername());
            return ModelAndViewBuilder.forRedirect("/user/signin").toModelAndView();
        }
    }

    private boolean isUserRegistrationDisabled() {
        return !gridWarsProperties.getRegistration().getEnabled();
    }

    private boolean hasProvidedValidRegistrationPassword(NewUserDto newUserDto) {
        String expectedRegistrationPassword = getCompetitionRegistrationPassword();
        if (StringUtils.hasLength(expectedRegistrationPassword)) {
            return expectedRegistrationPassword.equals(newUserDto.getRegistrationPassword());
        } else {
            return true;
        }
    }

    private String getCompetitionRegistrationPassword() {
        return gridWarsProperties.getRegistration().getRegistrationPassword();
    }

    private void preprocessNewUser(NewUserDto newUserDto, HttpServletRequest request) {
        newUserDto.setUsername(trim(newUserDto.getUsername()).toLowerCase());
        newUserDto.setTeamName(trim(newUserDto.getTeamName()));
        newUserDto.setEmail(trim(newUserDto.getEmail()));
        newUserDto.setIp(request.getRemoteAddr());
    }

    @GetMapping("/confirm/{confirmationId}")
    public ModelAndView confirmUser(@PathVariable String confirmationId, RedirectAttributes redirectAttributes) {
        return userService.getByConfirmationId(confirmationId)
            .filter(user -> !user.isConfirmed())
            .map(user -> {
                userService.confirmUser(user.getId());
                redirectAttributes.addFlashAttribute("confirmed", user.getUsername());
                return ModelAndViewBuilder.forRedirect("/user/signin").toModelAndView();
            })
            .orElseThrow(NotFoundException::new);
    }

    @GetMapping("/update")
    public ModelAndView showUpdateUser(@AuthenticationPrincipal User currentUser) {
        return userService.getById(currentUser.getId())
            .map(this::toUpdateUserDto)
            .map(updateUserDto ->
                ModelAndViewBuilder.forPage("user/update")
                    .addAttribute("user", updateUserDto)
                    .toModelAndView())
            .orElseThrow(NotFoundException::new);
    }

    private UpdateUserDto toUpdateUserDto(User user) {
        return new UpdateUserDto()
            .setUsername(user.getUsername())
            .setTeamName(user.getTeamName())
            .setEmail(user.getEmail());
    }

    @PostMapping("/update")
    public ModelAndView updateUser(@ModelAttribute("user") @Valid UpdateUserDto updateUserDto, BindingResult result,
                                   RedirectAttributes redirectAttributes, @AuthenticationPrincipal User currentUser) {
        if (!result.hasErrors()) {
            preprocessUpdatedUser(updateUserDto, currentUser);

            try {
                userService.update(updateUserDto);
            } catch (UserService.UserFieldValueException ufve) {
                result.rejectValue(ufve.getFieldName(), ufve.getErrorMessageCode());
            }
        }

        if (result.hasErrors()) {
            return ModelAndViewBuilder.forPage("user/update")
                .addAttribute("user", updateUserDto)
                .toModelAndView();
        } else {
            redirectAttributes.addFlashAttribute("success", true);
            return ModelAndViewBuilder.forRedirect("/user/update").toModelAndView();
        }
    }

    private void preprocessUpdatedUser(UpdateUserDto updateUserDto, User currentUser) {
        updateUserDto.setId(currentUser.getId());
        updateUserDto.setEmail(trim(updateUserDto.getEmail()));
    }

    private String trim(String value) {
        return (value != null) ? value.trim() : null;
    }
}