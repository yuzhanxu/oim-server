package com.oimchat.server.general.kernel.work.module.business.group.service;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oimchat.server.basic.common.util.KeyUtil;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupManageDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupMemberDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupRelationDAO;
import com.oimchat.server.general.kernel.work.module.base.group.data.query.GroupInviteVerifyQuery;
import com.oimchat.server.general.kernel.work.module.base.group.data.query.GroupJoinApplyQuery;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupInviteApply;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupJoinApply;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupMember;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupMemberManager;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupBusinessPush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupMemberPush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupRelationPush;
import com.onlyxiahui.aware.basic.work.business.error.ErrorCode;
import com.onlyxiahui.common.data.common.data.Page;
import com.onlyxiahui.common.message.bean.Info;

/**
 * 
 * Date 2019-01-23 22:01:34<br>
 * Description
 * 
 * @author XiaHui<br>
 * @since 1.0.0
 */
@Service
@Transactional
public class GroupManageService {

	@Resource
	private GroupDAO groupDAO;
	@Resource
	private GroupManageDAO groupManageDAO;
	@Resource
	private GroupMemberDAO groupMemberDAO;
	@Resource
	private GroupRelationDAO groupRelationDAO;
	@Resource
	private GroupManager groupManager;
	@Resource
	private GroupMemberManager groupMemberManager;
	@Resource
	private GroupMemberPush groupMemberPush;
	@Resource
	private GroupBusinessPush groupBusinessPush;
	@Resource
	private GroupRelationPush groupRelationPush;

	/**
	 * 
	 * Date 2019-01-24 22:08:14<br>
	 * Description ?????????????????????????????????????????????????????????
	 * 
	 * @author XiaHui<br>
	 * @param userId
	 * @param page
	 * @return
	 * @since 1.0.0
	 */
	public List<GroupJoinApply> getJoinRequestListByUserId(String userId, Page page) {
		List<GroupJoinApply> list = groupManageDAO.getJoinRequestListByUserId(userId, page);
		return list;
	}

	public List<GroupJoinApply> queryJoinRequestList(String userId, GroupJoinApplyQuery query, Page page) {
		List<GroupJoinApply> list = groupManageDAO.queryJoinRequestList(userId, query, page);
		return list;
	}

	/**
	 * 
	 * Date 2019-01-26 09:34:41<br>
	 * Description ????????????????????????????????????????????????
	 * 
	 * @author XiaHui<br>
	 * @param userId
	 * @param page
	 * @return
	 * @since 1.0.0
	 */
	public List<GroupInviteApply> queryInviteRequestList(String userId, GroupInviteVerifyQuery query, Page page) {
		List<GroupInviteApply> list = groupManageDAO.queryInviteRequestList(userId, query, page);
		return list;
	}

	/**
	 * 
	 * Date 2019-01-26 14:54:46<br>
	 * Description ????????????
	 * 
	 * @author XiaHui<br>
	 * @param groupId
	 * @param oldOwnerUserId
	 * @param newOwnerUserId
	 * @return
	 * @since 1.0.0
	 */
	public Info changeGroupOwner(String groupId, String oldOwnerUserId, String newOwnerUserId) {
		Info info = new Info();
		boolean isOwner = groupMemberManager.isOwner(groupId, oldOwnerUserId);
		// ?????????????????????
		if (isOwner) {
			// ???????????????????????????
			groupMemberDAO.updatePosition(groupId, oldOwnerUserId, GroupMember.position_normal);
			// ???????????????
			groupMemberDAO.updatePosition(groupId, newOwnerUserId, GroupMember.position_owner);

			// TODO????????????????????????
			List<String> userIds = new ArrayList<>();
			List<GroupMember> list = groupMemberDAO.getListByGroupId(groupId);
			for (GroupMember m : list) {
				userIds.add(m.getUserId());
			}
			groupMemberPush.pushUpdatePosition(userIds, KeyUtil.getKey(), groupId, oldOwnerUserId, GroupMember.position_normal);
			groupMemberPush.pushUpdatePosition(userIds, KeyUtil.getKey(), groupId, newOwnerUserId, GroupMember.position_owner);
		} else {
			info.addWarning(ErrorCode.business.code("001"), "???????????????");
		}
		return info;
	}

	/**
	 * 
	 * Date 2019-01-26 15:01:20<br>
	 * Description ?????????
	 * 
	 * @author XiaHui<br>
	 * @param groupId
	 * @param ownerUserId
	 * @return
	 * @since 1.0.0
	 */
	public Info disbandGroup(String groupId, String ownerUserId) {
		Info info = new Info();
		boolean isOwner = groupMemberManager.isOwner(groupId, ownerUserId);
		// ????????????????????????
		if (isOwner) {
			List<String> userIds = new ArrayList<>();
			List<GroupMember> list = groupMemberDAO.getListByGroupId(groupId);
			for (GroupMember m : list) {
				userIds.add(m.getUserId());
			}
			// ?????????????????????????????????
			groupMemberDAO.deleteOutUserIdByGroupId(groupId, ownerUserId);

			groupRelationDAO.deleteOutUserIdByGroupId(groupId, ownerUserId);

			// TODO????????????????????? userIds
			groupBusinessPush.pushDisband(ownerUserId, KeyUtil.getKey(), groupId, ownerUserId);

			// ?????????????????????????????????????????????
			userIds.remove(ownerUserId);
			groupRelationPush.pushDelete(userIds, KeyUtil.getKey(), groupId);
		} else {
			info.addWarning(ErrorCode.business.code("001"), "???????????????");
		}
		return info;
	}
}
