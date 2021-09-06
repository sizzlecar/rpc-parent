package io.kimmking.rpcfx.demo.provider;

import io.kimmking.rpcfx.annotation.RpcService;
import io.kimmking.rpcfx.demo.api.User;
import io.kimmking.rpcfx.demo.api.UserService;
import org.springframework.stereotype.Service;

@RpcService
@Service
public class UserServiceImpl implements UserService {

    public UserServiceImpl() {
    }

    @Override
    public User findById(int id) {
        return new User(id, "KK" + System.currentTimeMillis());
    }
}
