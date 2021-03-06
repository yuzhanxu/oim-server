package com.oimchat.server.general.kernel.work.module.business.group.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oimchat.server.basic.common.util.KeyUtil;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupJoinApplyDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupJoinVerifyAnswerDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupJoinVerifyQuestionDAO;
import com.oimchat.server.general.kernel.work.module.base.group.dao.GroupMemberDAO;
import com.oimchat.server.general.kernel.work.module.base.group.data.dto.GroupJoinApplyData;
import com.oimchat.server.general.kernel.work.module.base.group.data.dto.GroupJoinHandleData;
import com.oimchat.server.general.kernel.work.module.base.group.data.dto.GroupJoinResultData;
import com.oimchat.server.general.kernel.work.module.base.group.data.dto.JoinVerifyAnswer;
import com.oimchat.server.general.kernel.work.module.base.group.data.dto.JoinVerifyQuestion;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupJoinApply;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupJoinSetting;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupJoinVerifyAnswer;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupMember;
import com.oimchat.server.general.kernel.work.module.base.group.entity.GroupRelation;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupCategoryManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupJoinSettingManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupMemberManager;
import com.oimchat.server.general.kernel.work.module.base.group.manager.GroupRelationManager;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupJoinPush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupMemberPush;
import com.oimchat.server.general.kernel.work.module.base.group.push.GroupRelationPush;
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
public class GroupJoinService {

	@Resource
	private GroupMemberDAO groupMemberDAO;
	@Resource
	private GroupJoinApplyDAO groupJoinRequestDAO;
	@Resource
	private GroupJoinVerifyAnswerDAO groupJoinVerifyAnswerDAO;
	@Resource
	private GroupJoinVerifyQuestionDAO groupJoinVerifyQuestionDAO;

	@Resource
	private GroupJoinSettingManager groupJoinSettingManager;
	@Resource
	private GroupCategoryManager groupCategoryManager;
	@Resource
	private GroupMemberManager groupMemberManager;
	@Resource
	private GroupRelationManager groupRelationManager;

	@Resource
	private GroupMemberPush groupMemberPush;
	@Resource
	private GroupRelationPush groupRelationPush;
	@Resource
	private GroupJoinPush groupJoinPush;

	/**
	 * 
	 * Date 2019-01-24 22:24:41<br>
	 * Description ?????????????????????
	 * 
	 * @author XiaHui<br>
	 * @param request
	 * @param answerList
	 * @return
	 * @since 1.0.0
	 */
	public Info joinApply(GroupJoinApplyData request, List<JoinVerifyAnswer> answerList) {
		Info info = new Info();
		String applyUserId = request.getApplyUserId();
		String requestCategoryId = request.getCategoryId();
		// ?????????
		String requestRemark = request.getRemark();

		String groupId = request.getGroupId();

		String requestAnswer = request.getAnswer();

		GroupMember m = groupMemberDAO.getByGroupIdUserId(groupId, applyUserId);

		if (null != m) {
			info.addWarning("001", "?????????????????????");
			return info;
		}
		List<String> userList = new ArrayList<>(1);
		userList.add(applyUserId);
		List<GroupJoinApply> list = groupJoinRequestDAO.getUntreatedListByUserIds(groupId, userList);
		if (null != list && !list.isEmpty()) {
			return info;
		}
		// ?????????????????????
		GroupJoinSetting setting = groupJoinSettingManager.getByGroupId(groupId);

		String verifyType = setting.getJoinType();
		String settingAnswer = setting.getAnswer();

		String handleType = GroupJoinApply.handle_type_untreated;
		if (GroupJoinSetting.join_verify_type_never.equals(verifyType) || GroupJoinSetting.join_verify_type_invite.equals(verifyType)) {
			info.addWarning("002", "??????????????????");
			return info;
		} else if (GroupJoinSetting.join_verify_type_any.equals(verifyType)) {
			// ????????????????????????????????????????????????
			String categoryId = requestCategoryId != null && !"".equals(requestCategoryId) ? requestCategoryId : groupCategoryManager.getOrCreateDefaultCategoryId(applyUserId);

			handleType = GroupJoinApply.handle_type_accept;

			GroupJoinApply join = addGroupJoinRequest(handleType, request, answerList, setting);
			GroupRelation gr = groupRelationManager.getByGroupId(applyUserId, groupId);
			if (null == gr) {
				gr = groupRelationManager.add(
						groupId,
						applyUserId,
						categoryId,
						requestRemark);
			}

			if (!groupMemberManager.inGroup(groupId, applyUserId)) {
				groupMemberManager.add(groupId, applyUserId);
			}
			groupRelationPush.pushAdd(applyUserId, KeyUtil.getKey(), groupId);
			handleJoinResponse(join, "");
		} else if (GroupJoinSetting.join_verify_type_answer.equals(verifyType)) {
			// ?????????????????????????????????????????????????????????
			if (null == requestAnswer || requestAnswer.isEmpty()) {
				info.addWarning("001", "??????????????????");
				return info;
			}

			String ra = requestAnswer.toLowerCase().trim();
			String sa = settingAnswer.toLowerCase().trim();

			if (!ra.equals(sa)) {
				info.addWarning("002", "??????????????????");
				return info;
			}

			handleType = GroupJoinApply.handle_type_accept;
			String categoryId = requestCategoryId != null && !"".equals(requestCategoryId) ? requestCategoryId : groupCategoryManager.getOrCreateDefaultCategoryId(applyUserId);

			GroupJoinApply join = addGroupJoinRequest(handleType, request, answerList, setting);
			GroupRelation gr = groupRelationManager.getByGroupId(applyUserId, groupId);
			if (null == gr) {
				gr = groupRelationManager.add(
						groupId,
						applyUserId,
						categoryId,
						requestRemark);
			}

			if (!groupMemberManager.inGroup(groupId, applyUserId)) {
				groupMemberManager.add(groupId, applyUserId);
			}
			groupRelationPush.pushAdd(applyUserId, KeyUtil.getKey(), groupId);
			handleJoinResponse(join, "");
		} else if (GroupJoinSetting.join_verify_type_auth.equals(verifyType)) {
			// ?????????????????????????????????????????????????????????????????????
			GroupJoinApply join = addGroupJoinRequest(handleType, request, answerList, setting);
			String applyId = join.getId();// ????????????id
			// ?????????????????????
			List<String> userIds = groupMemberManager.getGroupAdminAndOwnerUserIdList(groupId);
			pushJoinApply(groupId, applyId, userIds);
		} else if (GroupJoinSetting.join_verify_type_confirm.equals(verifyType)) {
			GroupJoinApply joinRequest = addGroupJoinRequest(handleType, request, answerList, setting);
			String applyId = joinRequest.getId();// ????????????id
			// ?????????????????????
			List<String> userIds = groupMemberManager.getGroupAdminAndOwnerUserIdList(groupId);
			pushJoinApply(groupId, applyId, userIds);
		}
		return info;
	}

	private GroupJoinApply addGroupJoinRequest(String handleType, GroupJoinApplyData request, List<JoinVerifyAnswer> answerList, GroupJoinSetting setting) {

		String applyUserId = request.getApplyUserId();
		String requestCategoryId = request.getCategoryId();
		// ?????????
		String requestRemark = request.getRemark();

		String groupId = request.getGroupId();

		String requestAnswer = request.getAnswer();
		// ????????????
		String requestMessage = request.getMessage();

		// ?????????????????????

		String verifyType = setting.getJoinType();
		// String settingAnswer = setting.getAnswer();

		GroupJoinApply joinRequest = new GroupJoinApply();
		joinRequest.setApplyUserId(applyUserId);
		joinRequest.setGroupId(groupId);
		joinRequest.setCategoryId(requestCategoryId);
		joinRequest.setRemark(requestRemark);
		joinRequest.setVerifyType(verifyType);
		joinRequest.setCreatedDateTime(DateUtil.getCurrentDateTime());
		joinRequest.setQuestion(setting.getQuestion());
		joinRequest.setAnswer(requestAnswer);
		joinRequest.setHandleType(handleType);
		joinRequest.setHandleTimestamp(System.currentTimeMillis());
		joinRequest.setApplyMessage(requestMessage);

		groupJoinRequestDAO.save(joinRequest);

		if (GroupJoinSetting.join_verify_type_confirm.equals(verifyType) && null != answerList && !answerList.isEmpty()) {
			Map<String, JoinVerifyQuestion> questionMap = new HashMap<String, JoinVerifyQuestion>();
			List<JoinVerifyQuestion> questionList = groupJoinVerifyQuestionDAO.getListByGroupId(groupId, JoinVerifyQuestion.class);
			if (null != questionList && !questionList.isEmpty()) {
				for (JoinVerifyQuestion q : questionList) {
					questionMap.put(q.getId(), q);
				}
			}
			String applyId = joinRequest.getId();// ???????????????id(??????UserAddRequest??????id)

			List<GroupJoinVerifyAnswer> list = new ArrayList<GroupJoinVerifyAnswer>();
			for (JoinVerifyAnswer a : answerList) {

				String questionId = a.getQuestionId();// ??????id(??????GroupJoinVerifyQuestion??????id)
				String question = "";// ??????
				String answer = a.getAnswer();// ??????
				JoinVerifyQuestion q = questionMap.get(questionId);
				if (null != q) {
					question = q.getQuestion();
				}
				GroupJoinVerifyAnswer bean = new GroupJoinVerifyAnswer();
				bean.setApplyId(applyId);
				bean.setApplyUserId(applyUserId);
				bean.setGroupId(groupId);
				bean.setQuestionId(questionId);
				bean.setQuestion(question);
				bean.setAnswer(answer);
				bean.setCreatedDateTime(DateUtil.getCurrentDateTime());
				list.add(bean);
			}

			if (!list.isEmpty()) {
				groupJoinVerifyAnswerDAO.saveList(list);
			}
		}
		return joinRequest;
	}

	public Info joinHandle(GroupJoinHandleData handle) {
		// String handleUserId, String applyId,String handleType, String message

		List<String> applyIds = handle.getApplyIds();// ????????????id
		String handleUserId = handle.getHandleUserId();
		String handleType = handle.getHandleType();// ???????????????0:????????? 1:?????? 2:?????? 3:??????
		String message = handle.getMessage();// ????????????

		Info info = new Info();
		if (null != applyIds) {
			for (String applyId : applyIds) {
				GroupJoinApply joinApply = groupJoinRequestDAO.get(applyId);

				if (null != joinApply) {

					String categoryId = joinApply.getCategoryId();
					String groupId = joinApply.getGroupId();
					String applyUserId = joinApply.getApplyUserId();
					String remark = joinApply.getRemark();

					GroupMember m = groupMemberDAO.getByGroupIdUserId(groupId, applyUserId);

					if (null != m) {
						return info;
					}

					String handleUserPosition = groupMemberManager.getPosition(groupId, handleUserId);

					boolean isAdmin = groupMemberManager.isAdmin(handleUserPosition);
					boolean isOwner = groupMemberManager.isOwner(handleUserPosition);
					if (isAdmin || isOwner) {
						joinApply.setHandleType(handleType);
						joinApply.setHandleTimestamp(System.currentTimeMillis());
						joinApply.setHandleUserId(handleUserId);
						joinApply.setHandleUserPosition(handleUserPosition);

						groupJoinRequestDAO.update(joinApply);

						if (GroupJoinApply.handle_type_accept.equals(handleType)) {

							GroupRelation gr = groupRelationManager.getByGroupId(applyUserId, groupId);
							if (null == gr) {
								gr = groupRelationManager.add(
										groupId,
										applyUserId,
										categoryId,
										remark);
							}

							if (!groupMemberManager.inGroup(groupId, applyUserId)) {
								groupMemberManager.add(groupId, applyUserId);
							}

							List<String> userIds = groupMemberManager.getGroupMemberUserIdList(groupId);
							groupRelationPush.pushAdd(applyUserId, KeyUtil.getKey(), groupId);
							groupMemberPush.pushAdd(userIds, KeyUtil.getKey(), groupId, applyUserId);
						}
					}
					handleJoinResponse(joinApply, message);
				}
			}
		}
		return info;
	}

	private void pushJoinApply(String groupId, String applyId, List<String> userIds) {
		groupJoinPush.pushJoinApply(userIds, KeyUtil.getKey(), groupId, applyId);
	}

	private void handleJoinResponse(GroupJoinApply joinApply, String message) {

		String applyId = joinApply.getId();
		String groupId = joinApply.getGroupId();
		String applyUserId = joinApply.getApplyUserId();

		String handleUserId = joinApply.getHandleUserId();
		String handleType = joinApply.getHandleType();// ???????????????0:????????? 1:?????? 2:?????? 3:??????
		// String message = joinApply.getMessage();

		GroupJoinResultData result = new GroupJoinResultData();
		result.setApplyId(applyId);
		result.setGroupId(groupId);
		result.setHandleType(handleType);
		result.setHandleUserId(handleUserId);
		// result.setMessage(message);
		groupJoinPush.pushJoinHandle(applyUserId, KeyUtil.getKey(), result);
	}
}
