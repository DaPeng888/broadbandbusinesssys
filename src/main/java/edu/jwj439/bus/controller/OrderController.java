package edu.jwj439.bus.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import edu.jwj439.bus.service.ICustomerService;
import edu.jwj439.bus.service.IOrderService;
import edu.jwj439.bus.vo.CustomerVo;
import edu.jwj439.bus.vo.OrderVo;
import edu.jwj439.stats.utils.ExprotCustomerUtil;
import edu.jwj439.stats.utils.ZXingCodeEncodeUtil;
import edu.jwj439.sys.constast.SysConstast;
import edu.jwj439.sys.entity.User;
import edu.jwj439.sys.utils.DataGridView;
import edu.jwj439.sys.utils.RandomUtils;
import edu.jwj439.sys.utils.ResultObj;
import edu.jwj439.sys.utils.WebUtils;

@RestController
@RequestMapping("order")
public class OrderController {
    @Autowired
    private IOrderService orderService;

    @Autowired
    private ICustomerService customerService;

    @RequestMapping("loadAllOrder")
    public DataGridView loadAllOrder(OrderVo orderVo) {
        return this.orderService.queryAllOrder(orderVo);
    }

    /**
     * 初始化添加订单的表单数据
     * 
     * @param orderVo
     * @return
     */
    @RequestMapping("initOrderForm")
    public OrderVo initForm(OrderVo orderVo) {
        // 生成订单号
        orderVo.setOrderNumber(RandomUtils.createRandomStringUseTime(SysConstast.ORDER_HEAD_GH));
        // 生成计费日期
        /* orderVo.setOrderStarttime(RandomUtils.firstDateOfNextMonth(new Date())); */
        orderVo.setOrderStarttime(new Date());
        // 设置操作员
        User user = (User) WebUtils.getHttpSession().getAttribute("user");
        orderVo.setOrderOperator(user.getRealname());
        if (orderVo.getOrderFeetype() != null) {
            switch (orderVo.getOrderFeetype()) {
                case 1:
                    orderVo.setOrderEndtime(
                            RandomUtils.FeetypeOfYearly(orderVo.getOrderStarttime()));
                    break;
                case 0:
                    orderVo.setOrderEndtime(
                            RandomUtils.FeetypeOfMonthly(orderVo.getOrderStarttime()));
                    break;
                default:
                    break;
            }
        }

        return orderVo;
    }

    @RequestMapping("saveRenewOrder")
    public ResultObj renewOrder(OrderVo orderVo) {
        try {
            User user = (User) WebUtils.getHttpSession().getAttribute("user");
            orderVo.setOrderOperator(user.getRealname());
            orderVo.setOrderCreatetime(new Date());
            orderVo.setOrderState(SysConstast.ORDER_STATE_WAIT);// 订单状态默认等待中
            this.orderService.addOrder(orderVo);
            this.orderService.updateOrderState();
            return ResultObj.UPDATE_SUCCESS;
        } catch (Exception e) {
            return ResultObj.UPDATE_ERROR;
        }
    }

    @RequestMapping("saveOrder")
    public ResultObj saveOrder(OrderVo orderVo) {
        try {
            // 保存修改时间
            orderVo.setOrderCreatetime(new Date());
            orderVo.setOrderState(SysConstast.ORDER_STATE_WAIT);// 订单状态默认等待中
            CustomerVo customerVo = new CustomerVo();
            customerVo.setCustLevel(orderVo.getOrderFeetype());
            customerVo.setCustName(orderVo.getOrderCustName());
            customerService.updateCustomerLevel(customerVo);
            this.orderService.addOrder(orderVo);
            this.orderService.updateOrderState();
            return ResultObj.ADD_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.ADD_ERROR;
        }
    }

    /**
     * 删除订单信息
     * 
     * @param OrderVo
     * @return
     */
    @RequestMapping("deleteOrder")
    public ResultObj deleteOrder(OrderVo OrderVo) {
        try {
            // 删除
            this.orderService.deleteOrder(OrderVo.getOrderId());
            return ResultObj.DELETE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.DELETE_ERROR;
        }
    }

    /**
     * 修改订单信息
     * 
     * @param OrderVo
     * @return
     */
    @RequestMapping("updateOrder")
    public ResultObj updateOrder(OrderVo orderVo) {
        try {
            // 修改
            this.orderService.updateOrder(orderVo);
            CustomerVo customerVo = new CustomerVo();
            customerVo.setCustLevel(orderVo.getOrderFeetype());
            customerVo.setCustName(orderVo.getOrderCustName());
            this.customerService.updateCustomerLevel(customerVo);
            this.orderService.updateOrderState();
            return ResultObj.UPDATE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.UPDATE_ERROR;
        }
    }

    /**
     * 
     * @param orderVo
     * @return
     */
    @RequestMapping("updateOrderDate")
    public ResultObj updateOrderDate(OrderVo orderVo) {
        try {
            // 修改
            int flag = orderVo.getOrderFeetype();
            switch (flag) {
                case 1:
                    orderVo.setOrderEndtime(RandomUtils.FeetypeOfYearly(orderVo.getOrderEndtime()));
                    break;
                case 0:
                    orderVo.setOrderEndtime(
                            RandomUtils.FeetypeOfMonthly(orderVo.getOrderEndtime()));
                    break;
                default:
                    break;
            }

            this.orderService.updateOrder(orderVo);
            this.orderService.updateOrderState();
            return ResultObj.UPDATE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.UPDATE_ERROR;
        }
    }

    @RequestMapping("getRenewDate")
    public OrderVo getRenewDate(OrderVo orderVo) {
        //新订单号
        orderVo.setOrderNumber(RandomUtils.createRandomStringUseTime(SysConstast.ORDER_HEAD_GH));
        switch (orderVo.getOrderFeetype()) {
            case 0:
                orderVo.setOrderEndtime(RandomUtils.FeetypeOfMonthly(orderVo.getOrderStarttime()));
                break;
            case 1:
                orderVo.setOrderEndtime(RandomUtils.FeetypeOfYearly(orderVo.getOrderStarttime()));
                break;
            default:
                break;
        }
        return orderVo;
    }

    @RequestMapping("getQRCode")
    public void getQRCode(HttpServletResponse response, HttpSession session, String str)
            throws IOException {
        // 定义图形验证码的长和宽
        // 画图片
        InputStream logoStream =
                ExprotCustomerUtil.class.getClassLoader().getResourceAsStream("logo.jpg");
        BufferedImage image = ZXingCodeEncodeUtil.createZxingCodeUseLogo(str, 150, 150, logoStream);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "JPEG", bos);
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        ServletOutputStream outputStream = response.getOutputStream();
        ImageIO.write(image, "JPEG", outputStream);
    }
}
