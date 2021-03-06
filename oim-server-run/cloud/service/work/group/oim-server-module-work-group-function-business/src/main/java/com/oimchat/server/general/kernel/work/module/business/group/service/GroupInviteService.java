package com.oimchat.server.general.kernel.work.module.business.group.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oimchat.server.basic.common.util.KeyUtil;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupInviteApplyDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupMemberDAO;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupInviteApply;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupJoinApply;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupJoinSetting;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupMember;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupRelation;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupCategoryManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupJoinSettingManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupMemberManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupRelationManager;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupInvitePush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupMemberPush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupRelationPush;
import com.onlyxiahui.aware.basic.work.business.error.ErrorCode;
import com.onlyxiahui.common.message.bean.Info;
import com.onlyxiahui.common.utils.base.util.time.DateUtil;

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
public class GroupInviteService {

	@Resource
	private GroupMemberDAO groupMemberDAO;
	@Resource
	private GroupInviteApplyDAO groupInviteApplyDAO;
	@Resource
	private GroupManager groupManager;
	@Resource
	private GroupMemberManager groupMemberManager;
	@Resource
	private GroupRelationManager groupRelationManager;
	@Resource
	private GroupCategoryManager groupCategoryManager;
	@Resource
	private GroupJoinSettingManager groupJoinSettingManager;
	@Resource
	private GroupInvitePush groupInvitePush;
	@Resource
	private GroupMemberPush groupMemberPush;
	@Resource
	private GroupRelationPush groupRelationPush;

	/**
	 * 
	 * Date 2019-01-26 23:23:05<br>
	 * Description ???????????????
	 * 
	 * @author XiaHui<br>
	 * @param inviterUserId
	 * @param groupId
	 * @param userIds
	 * @return
	 * @since 1.0.0
	 */
	public Info invite(String inviterUserId, String groupId, List<String> userIds) {

		Info info = new Info();

		if (null == groupId || null == userIds || userIds.isEmpty()) {
			info.addWarning(ErrorCode.business.code("001"), "????????????");
			return info;
		}

		if (userIds.size() > 1000) {
			info.addWarning(ErrorCode.business.code("002"), "????????????");
			return info;
		}

		boolean hasGroup = groupManager.has(groupId);

		if (!hasGroup) {
			info.addWarning(ErrorCode.business.code("001"), "????????????");
			return info;
		}

		String inviterUserPosition = groupMemberManager.getPosition(groupId, inviterUserId);

		String groupOwnerUserId = groupMemberManager.getGroupOwnerUserId(groupId);

		// ????????????????????????
		boolean inviterIsAdmin = groupMemberManager.isAdmin(inviterUserPosition);
		boolean inviterIsOwner = groupMemberManager.isOwner(inviterUserPosition);
		boolean inviterIsNormal = groupMemberManager.isNormal(inviterUserPosition);

		// ????????????????????????????????????????????????????????????????????????
		if (!inviterIsAdmin && !inviterIsOwner && !inviterIsNormal) {
			info.addWarning(ErrorCode.business.code("001"), "????????????");
			return info;
		}

		// ?????????????????????
		GroupJoinSetting setting = groupJoinSettingManager.getByGroupId(groupId);
		String verifyType = setting.getInviteType();
		String joinType = setting.getJoinType();

		String inviteeHandleType = GroupInviteApply.verify_handle_type_untreated;

		String verifyHandleType = GroupInviteApply.verify_handle_type_untreated;
		String verifyUserId = "0";
		String verifyUserPosition = "0";

		// ??????????????????????????????????????????
		userIds = filterExisted(groupId, userIds);

		if (GroupJoinSetting.invite_type_never.equals(verifyType)
				|| GroupJoinSetting.join_verify_type_never.equals(joinType)) {
			// ?????????????????????????????????????????????
			Long verifyHandleTimestamp = System.currentTimeMillis();
			if (inviterIsOwner) {
				// ????????????????????????????????????????????????????????????
				verifyHandleType = GroupInviteApply.verify_handle_type_accept;
				verifyUserId = inviterUserId;
				verifyUserPosition = inviterUserPosition;

				for (String inviteeUserId : userIds) {

					GroupInviteApply apply = new GroupInviteApply();
					apply.setGroupId(groupId);
					apply.setInviterUserId(inviterUserId);
					apply.setInviterUserPosition(inviterUserPosition);

					apply.setInviteeUserId(inviteeUserId);
					apply.setInviteeHandleType(inviteeHandleType);

					apply.setVerifyType(verifyType);
					apply.setVerifyHandleType(verifyHandleType);
					apply.setVerifyHandleTimestamp(verifyHandleTimestamp);
					apply.setVerifyUserId(verifyUserId);
					apply.setVerifyUserPosition(verifyUserPosition);
					apply.setInviterMessage("");
					apply.setCreatedDateTime(DateUtil.getCurrentDateTime());
					groupInviteApplyDAO.save(apply);

					// TODO???????????????????????????????????????/????????????
					groupInvitePush.pushInvite(inviteeUserId, KeyUtil.getKey(), groupId, apply.getId());
				}

			} else {
				info.addWarning(ErrorCode.business.code("001"), "??????????????????");
			}
		} else if (GroupJoinSetting.invite_type_any.equals(verifyType)) {

			// ??????????????????????????????
			Long verifyHandleTimestamp = System.currentTimeMillis();
			verifyHandleType = GroupInviteApply.verify_handle_type_accept;

			if (inviterIsAdmin || inviterIsOwner) {
				// ??????????????????????????????????????????????????????
				verifyUserId = inviterUserId;
				verifyUserPosition = inviterUserPosition;
			} else {
				verifyUserId = groupOwnerUserId;
				verifyUserPosition = GroupMember.position_owner;
			}

			for (String inviteeUserId : userIds) {
				GroupInviteApply apply = new GroupInviteApply();

				apply.setGroupId(groupId);

				apply.setInviterUserId(inviterUserId);
				apply.setInviterUserPosition(inviterUserPosition);

				apply.setInviteeUserId(inviteeUserId);
				apply.setInviteeHandleType(inviteeHandleType);

				apply.setInviteeHandleType(inviteeHandleType);
				// apply.setInviterHandleDateTime(DateUtil.getCurrentDateTime());

				apply.setVerifyType(verifyType);
				apply.setVerifyHandleType(verifyHandleType);
				apply.setVerifyHandleTimestamp(verifyHandleTimestamp);
				apply.setVerifyUserId(verifyUserId);
				apply.setVerifyUserPosition(verifyUserPosition);
				apply.setInviterMessage("");
				apply.setCreatedDateTime(DateUtil.getCurrentDateTime());

				groupInviteApplyDAO.save(apply);

				if (GroupInviteApply.verify_handle_type_accept.equals(verifyHandleType)) {
					// TODO???????????????????????????????????????/????????????
					groupInvitePush.pushInvite(KeyUtil.getKey(), groupId, apply.getId(), inviteeUserId);
				}
			}
		} else if (GroupJoinSetting.invite_type_admin.equals(verifyType)) {

			// ????????????????????????????????????

			Long verifyHandleTimestamp = System.currentTimeMillis();
			verifyHandleType = GroupInviteApply.verify_handle_type_accept;

			if (inviterIsAdmin || inviterIsOwner) {
				verifyUserId = inviterUserId;
				verifyUserPosition = inviterUserPosition;

				for (String inviteeUserId : userIds) {
					GroupInviteApply apply = new GroupInviteApply();

					apply.setGroupId(groupId);

					apply.setInviterUserId(inviterUserId);
					apply.setInviterUserPosition(inviterUserPosition);

					apply.setInviteeUserId(inviteeUserId);
					apply.setInviteeHandleType(inviteeHandleType);
					apply.setInviterHandleTimestamp(System.currentTimeMillis());

					apply.setVerifyType(verifyType);

					apply.setVerifyHandleType(verifyHandleType);
					apply.setVerifyHandleTimestamp(verifyHandleTimestamp);
					apply.setVerifyUserId(verifyUserId);
					apply.setVerifyUserPosition(verifyUserPosition);
					apply.setInviterMessage("");
					apply.setCreatedDateTime(DateUtil.getCurrentDateTime());

					groupInviteApplyDAO.save(apply);

					if (GroupInviteApply.verify_handle_type_accept.equals(verifyHandleType)) {
						// TODO???????????????????????????????????????/????????????
						groupInvitePush.pushInvite(KeyUtil.getKey(), groupId, apply.getId(), inviteeUserId);
					}
				}
			} else {
				info.addWarning(ErrorCode.business.code("002"), "?????????????????????");
			}
		} else if (GroupJoinSetting.invite_type_auth.equals(verifyType)) {

			// ????????????????????????????????????

			Long verifyHandleTimestamp = System.currentTimeMillis();

			if (inviterIsAdmin || inviterIsOwner) {
				// ???????????????????????????????????????????????????
				verifyHandleType = GroupInviteApply.verify_handle_type_accept;
				verifyUserId = inviterUserId;
				verifyUserPosition = inviterUserPosition;

			}
			List<String> adminAndOwnerUserIds = groupMemberManager.getGroupAdminAndOwnerUserIdList(groupId);
			for (String inviteeUserId : userIds) {
				GroupInviteApply apply = new GroupInviteApply();

				apply.setGroupId(groupId);

				apply.setInviterUserId(inviterUserId);
				apply.setInviterUserPosition(inviterUserPosition);

				apply.setInviteeUserId(inviteeUserId);
				apply.setInviteeHandleType(inviteeHandleType);
				apply.setInviterHandleTimestamp(System.currentTimeMillis());

				apply.setVerifyType(verifyType);
				apply.setVerifyHandleType(verifyHandleType);
				apply.setVerifyHandleTimestamp(verifyHandleTimestamp);
				apply.setVerifyUserId(verifyUserId);
				apply.setVerifyUserPosition(verifyUserPosition);
				apply.setInviterMessage("");
				apply.setCreatedDateTime(DateUtil.getCurrentDateTime());
				groupInviteApplyDAO.save(apply);

				if (GroupInviteApply.verify_handle_type_accept.equals(verifyHandleType)) {
					// TODO???????????????????????????????????????/????????????
					groupInvitePush.pushInvite(inviteeUserId, KeyUtil.getKey(), groupId, apply.getId());
				} else {
					groupInvitePush.pushInviteApply(adminAndOwnerUserIds, KeyUtil.getKey(), groupId, apply.getId());
				}
			}
		}
		return info;
	}

	/**
	 * 
	 * Date 2019-01-26 23:49:54<br>
	 * Description ???????????????????????????????????????
	 * 
	 * @author XiaHui<br>
	 * @param verifyHandleUserId
	 * @param inviteApplyId
	 * @param verifyHandleType
	 * @return
	 * @since 1.0.0
	 */
	public Info verifyHandle(String verifyHandleUserId, List<String> inviteApplyIds, String verifyHandleType) {
		Info info = new Info();
		if (null != inviteApplyIds) {
			for (String inviteApplyId : inviteApplyIds) {
				GroupInviteApply inviteApply = groupInviteApplyDAO.get(inviteApplyId);

				if (null != inviteApply
						&& GroupInviteApply.verify_handle_type_untreated.equals(inviteApply.getVerifyHandleType())) {
					String groupId = inviteApply.getGroupId();

					String verifyHandleUserPosition = groupMemberManager.getPosition(groupId, verifyHandleUserId);

					boolean isAdmin = groupMemberManager.isAdmin(verifyHandleUserPosition);
					boolean isOwner = groupMemberManager.isOwner(verifyHandleUserPosition);
					if (isAdmin || isOwner) {

						inviteApply.setVerifyHandleType(verifyHandleType);
						inviteApply.setVerifyUserPosition(verifyHandleUserPosition);
						inviteApply.setVerifyUserId(verifyHandleUserId);
						inviteApply.setVerifyHandleTimestamp(System.currentTimeMillis());
						groupInviteApplyDAO.update(inviteApply);
						// TODO ?????????????????????????????????
						String inviteeUserId = inviteApply.getInviteeUserId();
						if (GroupInviteApply.verify_handle_type_accept.equals(verifyHandleType)) {
							// TODO???????????????????????????????????????/????????????
							groupInvitePush.pushInvite(inviteeUserId, KeyUtil.getKey(), groupId, inviteApplyId);
						}
					} else {
						info.addWarning(ErrorCode.business.code("002"), "???????????????");
					}
				} else {
					info.addWarning(ErrorCode.business.code("001"), "?????????????????????");
				}
			}
		}
		return info;
	}

	/**
	 * 
	 * Date 2019-01-26 23:55:25<br>
	 * Description ???????????????????????????????????????/??????
	 * 
	 * @author XiaHui<br>
	 * @param inviteeUserId
	 * @param inviteApplyId
	 * @param inviteeHandleType
	 * @return
	 * @since 1.0.0
	 */
	public Info inviteeHandle(String inviteeUserId, String inviteApplyId, String inviteeHandleType) {
		Info info = new Info();
		GroupInviteApply inviteApply = groupInviteApplyDAO.get(inviteApplyId);
		if (null != inviteApply && inviteeUserId.equals(inviteApply.getInviteeUserId())) {

			if (GroupInviteApply.verify_handle_type_untreated.equals(inviteApply.getInviteeHandleType())) {

				inviteApply.setInviteeHandleType(inviteeHandleType);
				groupInviteApplyDAO.update(inviteApply);

				String categoryId = groupCategoryManager.getOrCreateDefaultCategoryId(inviteeUserId);

				String groupId = inviteApply.getGroupId();
				String remark = "";

				if (GroupJoinApply.handle_type_accept.equals(inviteeHandleType)) {

					GroupRelation gr = groupRelationManager.getByGroupId(inviteeUserId, groupId);
					if (null == gr) {
						gr = groupRelationManager.add(groupId, inviteeUserId, categoryId, remark);
					}
					if (!groupMemberManager.inGroup(groupId, inviteeUserId)) {
						groupMemberManager.add(groupId, inviteeUserId);
					}
					List<String> userIds = groupMemberManager.getGroupMemberUserIdList(groupId);
					groupRelationPush.pushAdd(inviteeUserId, KeyUtil.getKey(), groupId);
					groupMemberPush.pushAdd(userIds, KeyUtil.getKey(), groupId, inviteeUserId);
				} else {

				}
			}
		} else {
			info.addWarning(ErrorCode.business.code("001"), "???????????????");
		}
		return info;
	}

	/**
	 * 
	 * Date 2019-01-26 23:59:07<br>
	 * Description ?????????????????????????????????/???????????????????????????
	 * 
	 * @author XiaHui<br>
	 * @param groupId
	 * @param userIds
	 * @return
	 * @since 1.0.0
	 */
	private List<String> filterExisted(String groupId, List<String> userIds) {

		List<GroupInviteApply> applys = groupInviteApplyDAO.getUntreatedListByInviteeUserIds(groupId, userIds);

		List<String> list = new ArrayList<>();
		List<GroupMember> members = groupMemberDAO.getListByGroupId(groupId);
		int size = members.size() + applys.size();
		Map<String, String> map = new HashMap<>(size);
		for (GroupMember m : members) {
			map.put(m.getUserId(), m.getUserId());
		}

		for (GroupInviteApply m : applys) {
			map.put(m.getInviteeUserId(), m.getInviteeUserId());
		}

		for (String id : userIds) {
			if (!map.containsKey(id)) {
				list.add(id);
			}
		}
		return list;
	}
}
