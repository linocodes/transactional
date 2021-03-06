package br.com.devcave.transactional.advanced.service;

import br.com.devcave.transactional.advanced.domain.Bill;
import br.com.devcave.transactional.advanced.domain.TypeEnum;
import br.com.devcave.transactional.advanced.domain.User;
import br.com.devcave.transactional.advanced.exception.TransactionalException;
import br.com.devcave.transactional.advanced.repository.BillRepository;
import br.com.devcave.transactional.advanced.repository.UserRepository;
import br.com.devcave.transactional.advanced.vo.BillVO;
import com.github.javafaker.Faker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private UserService userService;

    @Transactional(readOnly = true)
    public Double getTotalAmount() {
        log.info("M=getTotalAmount, start");
        List<User> userList = userRepository.findAll();
        log.info("M=getTotalAmount, totalUsers={}", userList.size());
        Double totalAmount = userList
                .stream()
                .mapToDouble(u -> u.getBillList()
                        .stream()
                        .mapToDouble(b -> b.getValue().doubleValue())
                        .sum())
                .sum();
        log.info("M=getTotalAmount, totalAmount={}", totalAmount);
        return totalAmount;
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        log.info("M=getUser, id={}", id);
        User user = userRepository.getOne(id);
        user.setName("Sr(a). " + user.getName());
        return user;
    }

    @Transactional
    public void addBill(Long id, TypeEnum type, BigDecimal value) {
        User user = userRepository.getOne(id);
        Bill bill = new Bill(type, value, LocalDate.now(), user);
        user.getBillList().add(bill);
    }

    @Transactional
    public void addUsers(String... documents) {
        List.of(documents).forEach(d -> userRepository.save(new User(
                new Faker(new Locale("pt", "BR")).name().name(),
                d, Collections.emptyList())));
    }

    @Transactional
    public void addBillsCheckedException(Long id, List<BillVO> billList) throws TransactionalException {
        User user = userRepository.getOne(id);
        for (BillVO billVO : billList) {
            validateBillWithCheckedException(billVO);
            user.getBillList().add(
                    new Bill(billVO.getType(),
                            billVO.getValue(), billVO.getDate(), user));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addBillsCheckedExceptionWithRollback(Long id, List<BillVO> billList) throws TransactionalException {
        User user = userRepository.getOne(id);
        for (BillVO billVO : billList) {
            validateBillWithCheckedException(billVO);
            user.getBillList().add(
                    new Bill(billVO.getType(),
                            billVO.getValue(), billVO.getDate(), user));
        }
    }

    private void validateBillWithCheckedException(BillVO bill) throws TransactionalException {
        if (bill.getDate().isAfter(LocalDate.now())) {
            throw new TransactionalException();
        }
    }

    @Transactional
    public void addBillsUncheckedException(Long id, List<BillVO> billList) {
        User user = userRepository.getOne(id);
        for (BillVO billVO : billList) {
            validateBillWithUncheckedException(billVO);
            user.getBillList().add(
                    new Bill(billVO.getType(),
                            billVO.getValue(), billVO.getDate(), user));
        }
    }

    @Transactional
    public void addBillsCatchingPrivateUncheckedException(final Long id, final List<BillVO> billList) {
        User user = userRepository.getOne(id);
        for (BillVO billVO : billList) {
            try {
                validateBillWithUncheckedException(billVO);
                user.getBillList().add(
                        new Bill(billVO.getType(),
                                billVO.getValue(), billVO.getDate(), user));
            } catch (RuntimeException e) {
                log.error("A fatura esta errada");
            }
        }
    }

    private void validateBillWithUncheckedException(BillVO bill) {
        if (bill.getDate().isAfter(LocalDate.now())) {
            throw new RuntimeException();
        }
    }

    @Transactional
    public void addBillsCatchingProxyUncheckedException(final Long id, final List<BillVO> billList) {
        User user = userRepository.getOne(id);
        for (BillVO billVO : billList) {
            try {
                validationService.validateBillWithUncheckedException(billVO);
                user.getBillList().add(
                        new Bill(billVO.getType(),
                                billVO.getValue(), billVO.getDate(), user));
            } catch (RuntimeException e) {
                log.error("A fatura esta errada");
            }
        }
    }

    @Transactional
    public void addBillsNewTransactionInThisClass(final Long id, final List<BillVO> billList) {
        User user = userRepository.getOne(id);
        for (BillVO billVO : billList) {
            createBill(billVO, user);
        }
        throw new RuntimeException();
    }

    @Transactional
    public void addBillsNewTransactionByProxy(final Long id, final List<BillVO> billList) {
        User user = userRepository.getOne(id);
        for (BillVO billVO : billList) {
            userService.createBill(billVO, user);
        }
        throw new RuntimeException();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createBill(BillVO billVO, User user) {
        final Bill bill = new Bill(billVO.getType(),
                billVO.getValue(), billVO.getDate(), user);
        billRepository.save(bill);
    }
}
