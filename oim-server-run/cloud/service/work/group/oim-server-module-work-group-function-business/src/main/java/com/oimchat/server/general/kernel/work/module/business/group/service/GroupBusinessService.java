package com.oimchat.server.general.kernel.work.module.business.group.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oimchat.server.basic.common.util.KeyUtil;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupCategoryDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupHeadDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupMemberDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupNumberDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupRelationDAO;
import com.oimchat.server.general.kernel.work.module.base.group.entity.Group;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupCategory;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupMember;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupRelation;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupCategoryManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupMemberManager;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupBusinessPush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupInfoPush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupMemberPush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupRelationPush;
import com.onlyxiahui.aware.basic.work.business.error.ErrorCode;
import com.onlyxiahui.common.message.bean.Info;
import com.onlyxiahui.common.message.result.ResultBodyMessage;
import com.onlyxiahui.common.utils.base.util.time.DateUtil;

/**
 * 
 * Date 2019-01-21 11:39:20<br>
 * Description
 * 
 * @author XiaHui<br>
 * @since 1.0.0
 */
@Service
@Transactional
public class GroupBusinessService {

	@Resource
	private GroupDAO groupDAO;
	@Resource
	private GroupNumberDAO groupNumberDAO;
	@Resource
	private GroupMemberDAO groupMemberDAO;
	@Resource
	private GroupCategoryDAO groupCategoryDAO;
	@Resource
	private GroupRelationDAO groupRelationDAO;
	@Resource
	private GroupHeadDAO groupHeadDAO;

	@Resource
	private GroupCategoryManager groupCategoryManager;
	@Resource
	private GroupMemberManager groupMemberManager;

	@Resource
	private GroupInfoPush groupInfoPush;
	@Resource
	private GroupBusinessPush groupBusinessPush;
	@Resource
	private GroupMemberPush groupMemberPush;
	@Resource
	private GroupRelationPush groupRelationPush;

	public ResultBodyMessage<Group> add(String key, String userId, Group group) {
		// ??????????????????
		Long number = groupNumberDAO.getNumber();

		group.setNumber(number);
		group.setCreatedDateTime(DateUtil.getCurrentDateTime());
		if (null == group.getHead() || "".equals(group.getHead())) {
			int i = new Random().nextInt(8);
			i = i + 1;
			group.setHead(i + "");
		}
		groupDAO.save(group);

		GroupCategory category = groupCategoryManager.getOrCreateDefault(userId);

		GroupRelation relation = new GroupRelation();
		relation.setUserId(userId);
		relation.setGroupId(group.getId());
		relation.setCategoryId(category.getId());

		groupRelationDAO.save(relation);

		GroupMember groupMember = new GroupMember();
		groupMember.setUserId(userId);
		groupMember.setGroupId(group.getId());
		groupMember.setPosition(GroupMember.position_owner);
		groupMemberDAO.save(groupMember);

		// TODO ?????????????????????
		// groupPush.pushAdd(KeyUtil.getKey(), group, userId);

		groupRelationPush.pushAdd(userId, KeyUtil.getKey(), group.getId());
		ResultBodyMessage<Group> message = new ResultBodyMessage<>(group);
		return message;
	}

	public Info update(String key, String userId, Group group) {
		Info info = new Info();
		if (null != group && null != group.getId()) {
			String groupId = group.getId();
			String position = groupMemberManager.getPosition(groupId, userId);
			boolean isOwner = groupMemberManager.isOwner(position);
			if (isOwner) {
				handleUpdate(group);
				groupDAO.updateSelective(group);
				List<String> userIds = groupMemberManager.getGroupMemberUserIdList(groupId);
				groupInfoPush.pushUpdate(userIds, KeyUtil.getKey(), groupId);
			}
		}
		return info;
	}

	public void handleUpdate(Group group) {
		if (null != group) {
			group.setNumber(null);
			group.setAvatar(null);
			group.setCreatedDateTime(null);
			group.setCreatedTimestamp(null);
		}
	}

	/**
	 * 
	 * Date 2019-01-27 11:08:11<br>
	 * Description ??????
	 * 
	 * @author XiaHui<br>
	 * @param groupId
	 * @param userId
	 * @return
	 * @since 1.0.0
	 */
	public Info quit(String groupId, String userId) {

		Info info = new Info();
		// ?????????????????????
		boolean isOwner = groupMemberManager.isOwner(groupId, userId);

		if (!isOwner) {
			boolean mark = groupMemberDAO.deleteByGroupIdUserId(groupId, userId);
			groupRelationDAO.deleteGroupCategoryMember(userId, groupId);

			if (!mark) {
				info.addWarning(ErrorCode.business.code("003"), "???????????????");
			} else {

				// TODO ????????????????????????????????????
				List<String> aoIds = groupMemberManager.getGroupAdminAndOwnerUserIdList(groupId);
				groupBusinessPush.pushQuit(aoIds, KeyUtil.getKey(), groupId, userId);

				List<String> userIds = groupMemberManager.getGroupMemberUserIdList(groupId);

				// ??????????????????????????????????????????????????????
				groupMemberPush.pushDelete(userIds, KeyUtil.getKey(), groupId, userId);

				List<String> ids = new ArrayList<>();
				ids.add(userId);
				// ?????????????????????????????????????????????????????????????????????????????????????????????
				groupRelationPush.pushDelete(ids, KeyUtil.getKey(), groupId);
			}
		} else if (isOwner) {
			info.addWarning(ErrorCode.business.code("001"), "????????????????????????");
		}
		return info;
	}
}
