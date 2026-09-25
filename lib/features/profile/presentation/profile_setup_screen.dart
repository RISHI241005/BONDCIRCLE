import 'package:flutter/material.dart';

import '../../../theme/bondcircle_theme.dart';
import '../../auth/domain/auth_session.dart';
import '../../circles/presentation/interest_circles_screen.dart';
import '../data/profile_api_service.dart';

class ProfileSetupScreen extends StatefulWidget {
  const ProfileSetupScreen({
    super.key,
    required this.initialName,
    this.profileApiService,
  });

  final String initialName;
  final ProfileApiService? profileApiService;

  @override
  State<ProfileSetupScreen> createState() => _ProfileSetupScreenState();
}

class _ProfileSetupScreenState extends State<ProfileSetupScreen> {
  static const _interestOptions = [
    ('Coffee', Icons.local_cafe_outlined),
    ('Books', Icons.menu_book_outlined),
    ('Fitness', Icons.fitness_center_rounded),
    ('Music', Icons.headphones_outlined),
    ('Travel', Icons.flight_takeoff_rounded),
    ('Startups', Icons.rocket_launch_outlined),
    ('Movies', Icons.movie_outlined),
    ('Food', Icons.restaurant_outlined),
  ];

  final _detailsKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  final _ageController = TextEditingController();
  final _cityController = TextEditingController();
  final _bioController = TextEditingController();
  final Set<String> _interests = {};
  final List<String> _customInterests = [];
  final Set<String> _datingPreferences = {};
  String? _gender;
  String? _sexuality;
  String? _datingIntention;
  String? _relationshipStyle;
  String? _childrenPlan;
  String? _religion;
  String? _politics;
  String? _drinking;
  String? _smoking;
  bool _showIdentity = true;
  bool _showValues = false;
  bool _showLifestyle = true;
  int _step = 0;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.initialName);
    _loadProfile();
  }

  Future<void> _loadProfile() async {
    if (!AuthSession.instance.isAuthenticated) return;
    final service = widget.profileApiService ?? ProfileApiService();
    final profile = await service.getProfile();
    if (profile != null && mounted) {
      setState(() {
        if (profile.gender.isNotEmpty) _gender = profile.gender;
        if (profile.orientation.isNotEmpty) _sexuality = profile.orientation;
        if (profile.connectionIntention.isNotEmpty) {
          _datingIntention = profile.connectionIntention;
        }
        if (profile.relationshipStyle.isNotEmpty) {
          _relationshipStyle = profile.relationshipStyle;
        }
        if (profile.interests.isNotEmpty) {
          _interests.clear();
          _interests.addAll(profile.interests);
          for (final interest in profile.interests) {
            final matchesOption = _interestOptions.any(
              (item) => item.$1.toLowerCase() == interest.toLowerCase(),
            );
            if (!matchesOption && !_customInterests.contains(interest)) {
              _customInterests.add(interest);
            }
          }
        }
      });
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _ageController.dispose();
    _cityController.dispose();
    _bioController.dispose();
    super.dispose();
  }

  void _next() {
    if (_step == 0 && !(_detailsKey.currentState?.validate() ?? false)) return;
    if (_step == 1 && _interests.length < 3) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Choose at least 3 interests.')),
      );
      return;
    }
    if (_step == 2 &&
        (_gender == null || _sexuality == null || _datingPreferences.isEmpty)) {
      _message('Complete the identity and dating preferences to continue.');
      return;
    }
    if (_step == 3 &&
        (_datingIntention == null ||
            _relationshipStyle == null ||
            _childrenPlan == null)) {
      _message('Choose your connection preferences to continue.');
      return;
    }
    if (_step == 4 &&
        (_religion == null ||
            _politics == null ||
            _drinking == null ||
            _smoking == null)) {
      _message('Complete the values and lifestyle section to continue.');
      return;
    }
    if (_step < 5) setState(() => _step++);
  }

  void _message(String text) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));

  void _back() {
    if (_step == 0) {
      Navigator.of(context).pop();
    } else {
      setState(() => _step--);
    }
  }

  void _finish() {
    Navigator.of(context).pushReplacement(
      MaterialPageRoute<void>(
        builder: (_) => ProfilePreviewScreen(
          name: _nameController.text.trim(),
          age: _ageController.text.trim(),
          city: _cityController.text.trim(),
          bio: _bioController.text.trim(),
          interests: _interests.toList(),
          gender: _gender!,
          datingIntention: _datingIntention!,
          relationshipStyle: _relationshipStyle!,
          datingPreferences: _datingPreferences.toList(),
          orientation: _sexuality ?? '',
          profileApiService: widget.profileApiService,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final titles = [
      'Let’s start with you',
      'What lights you up?',
      'Express who you are',
      'What are you looking for?',
      'Values and lifestyle',
      'Bring your profile to life',
    ];
    final subtitles = [
      'A few essentials help us make every introduction feel more relevant.',
      'Choose at least three interests. Shared energy starts great conversations.',
      'Choose what feels right. You control what appears on your profile.',
      'Clear intentions create kinder, more meaningful connections.',
      'Share only what feels comfortable. Every answer has a privacy control.',
      'Add photos that feel natural, current, and unmistakably you.',
    ];

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          key: const Key('profileBackButton'),
          onPressed: _back,
          icon: const Icon(Icons.arrow_back_rounded),
        ),
        title: const Text('Create profile'),
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 0),
              child: Row(
                children: List.generate(6, (index) {
                  return Expanded(
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      height: 5,
                      margin: EdgeInsets.only(right: index == 5 ? 0 : 7),
                      decoration: BoxDecoration(
                        color: index <= _step
                            ? BondCircleColors.primary
                            : BondCircleColors.border,
                        borderRadius: BorderRadius.circular(99),
                      ),
                    ),
                  );
                }),
              ),
            ),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      titles[_step],
                      style: Theme.of(context).textTheme.displaySmall,
                    ),
                    const SizedBox(height: 10),
                    Text(
                      subtitles[_step],
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                    const SizedBox(height: 28),
                    if (_step == 0) _detailsStep(),
                    if (_step == 1) _interestsStep(),
                    if (_step == 2) _identityStep(),
                    if (_step == 3) _intentionsStep(),
                    if (_step == 4) _valuesStep(),
                    if (_step == 5) _photosStep(),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 20),
              child: FilledButton(
                key: const Key('profileContinueButton'),
                onPressed: _step == 5 ? _finish : _next,
                child: Text(_step == 5 ? 'Preview profile' : 'Continue'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _detailsStep() {
    return Form(
      key: _detailsKey,
      child: Column(
        children: [
          TextFormField(
            key: const Key('profileNameField'),
            controller: _nameController,
            decoration: const InputDecoration(
              labelText: 'Name',
              prefixIcon: Icon(Icons.person_outline_rounded),
            ),
            validator: (value) =>
                (value ?? '').trim().length < 2 ? 'Enter your name' : null,
          ),
          const SizedBox(height: 16),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: TextFormField(
                  key: const Key('profileAgeField'),
                  controller: _ageController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Age'),
                  validator: (value) {
                    final age = int.tryParse((value ?? '').trim());
                    return age == null || age < 18 || age > 99
                        ? 'Enter age 18–99'
                        : null;
                  },
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                flex: 2,
                child: TextFormField(
                  key: const Key('profileCityField'),
                  controller: _cityController,
                  decoration: const InputDecoration(labelText: 'City'),
                  validator: (value) =>
                      (value ?? '').trim().isEmpty ? 'Enter your city' : null,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          TextFormField(
            key: const Key('profileBioField'),
            controller: _bioController,
            maxLines: 4,
            maxLength: 180,
            decoration: const InputDecoration(
              labelText: 'Short introduction',
              hintText: 'What should someone know about you?',
              alignLabelWithHint: true,
            ),
            validator: (value) => (value ?? '').trim().length < 20
                ? 'Write at least 20 characters'
                : null,
          ),
        ],
      ),
    );
  }

  Widget _interestsStep() {
    return Wrap(
      spacing: 10,
      runSpacing: 12,
      children: [
        ..._interestOptions.map((option) {
          final selected = _interests.contains(option.$1);
          return FilterChip(
            key: Key('interest${option.$1}'),
            selected: selected,
            showCheckmark: false,
            avatar: Icon(option.$2, size: 19),
            label: Text(option.$1),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
            onSelected: (_) => setState(() {
              selected
                  ? _interests.remove(option.$1)
                  : _interests.add(option.$1);
            }),
          );
        }),
        ..._customInterests.map((interest) {
          final selected = _interests.contains(interest);
          return FilterChip(
            key: Key('interest$interest'),
            selected: selected,
            showCheckmark: false,
            avatar: const Icon(Icons.auto_awesome_rounded, size: 19),
            label: Text(interest),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
            onSelected: (_) => setState(() {
              selected ? _interests.remove(interest) : _interests.add(interest);
            }),
          );
        }),
        ActionChip(
          key: const Key('addCustomInterestButton'),
          avatar: const Icon(Icons.add_rounded, size: 20),
          label: const Text('Add interest'),
          side: const BorderSide(color: BondCircleColors.primary),
          labelStyle: const TextStyle(
            color: BondCircleColors.primary,
            fontWeight: FontWeight.w700,
          ),
          backgroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
          onPressed: _showAddInterestDialog,
        ),
      ],
    );
  }

  Future<void> _showAddInterestDialog() async {
    final controller = TextEditingController();
    final formKey = GlobalKey<FormState>();
    final addedInterest = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Add your interest'),
        content: Form(
          key: formKey,
          child: TextFormField(
            key: const Key('customInterestField'),
            controller: controller,
            autofocus: true,
            textCapitalization: TextCapitalization.words,
            maxLength: 24,
            decoration: const InputDecoration(
              hintText: 'e.g. Photography',
              prefixIcon: Icon(Icons.interests_outlined),
            ),
            validator: (value) {
              final interest = (value ?? '').trim();
              if (interest.length < 2) return 'Enter an interest';
              final existing = [
                ..._interestOptions.map((item) => item.$1),
                ..._customInterests,
              ];
              if (existing.any(
                (item) => item.toLowerCase() == interest.toLowerCase(),
              )) {
                return 'This interest is already listed';
              }
              return null;
            },
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            key: const Key('confirmCustomInterestButton'),
            onPressed: () {
              if (formKey.currentState?.validate() ?? false) {
                Navigator.of(dialogContext).pop(controller.text.trim());
              }
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );
    if (addedInterest != null && mounted) {
      setState(() {
        _customInterests.add(addedInterest);
        _interests.add(addedInterest);
      });
    }
  }

  Widget _identityStep() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionLabel(
          icon: Icons.person_outline_rounded,
          title: 'Gender',
          subtitle: 'Choose what best describes you.',
        ),
        _singleChoice(
          keyPrefix: 'gender',
          options: const ['Man', 'Woman', 'Nonbinary', 'Self-described'],
          value: _gender,
          onChanged: (value) => setState(() => _gender = value),
        ),
        const SizedBox(height: 26),
        _SectionLabel(
          icon: Icons.favorite_border_rounded,
          title: 'Sexual orientation',
          subtitle: 'This helps us suggest more relevant people.',
        ),
        _singleChoice(
          keyPrefix: 'sexuality',
          options: const [
            'Straight',
            'Gay',
            'Lesbian',
            'Bisexual',
            'Queer',
            'Prefer not to say',
          ],
          value: _sexuality,
          onChanged: (value) => setState(() => _sexuality = value),
        ),
        const SizedBox(height: 26),
        _SectionLabel(
          icon: Icons.people_alt_outlined,
          title: 'Open to connecting with',
          subtitle: 'Select all that apply.',
        ),
        _multiChoice(
          keyPrefix: 'datingPreference',
          options: const ['Men', 'Women', 'Nonbinary people'],
          selected: _datingPreferences,
        ),
        const SizedBox(height: 18),
        _PrivacyToggle(
          value: _showIdentity,
          onChanged: (value) => setState(() => _showIdentity = value),
          label: _showIdentity
              ? 'Identity details visible'
              : 'Identity details private',
        ),
      ],
    );
  }

  Widget _intentionsStep() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionLabel(
          icon: Icons.explore_outlined,
          title: 'Connection intention',
          subtitle: 'What kind of connection are you open to?',
        ),
        _singleChoice(
          keyPrefix: 'intention',
          options: const [
            'Life partner',
            'Long-term relationship',
            'Short-term, open to long',
            'Figuring out my goals',
          ],
          value: _datingIntention,
          onChanged: (value) => setState(() => _datingIntention = value),
        ),
        const SizedBox(height: 26),
        _SectionLabel(
          icon: Icons.join_inner_rounded,
          title: 'Relationship style',
          subtitle: 'Choose the option that fits you today.',
        ),
        _singleChoice(
          keyPrefix: 'relationshipStyle',
          options: const ['Monogamy', 'Non-monogamy', 'Still figuring it out'],
          value: _relationshipStyle,
          onChanged: (value) => setState(() => _relationshipStyle = value),
        ),
        const SizedBox(height: 26),
        _SectionLabel(
          icon: Icons.family_restroom_rounded,
          title: 'Thoughts about children',
          subtitle: 'You can always change this later.',
        ),
        _singleChoice(
          keyPrefix: 'children',
          options: const [
            'Want children',
            'Open to children',
            'Don’t want children',
            'Not sure yet',
            'Prefer not to say',
          ],
          value: _childrenPlan,
          onChanged: (value) => setState(() => _childrenPlan = value),
        ),
      ],
    );
  }

  Widget _valuesStep() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionLabel(
          icon: Icons.diversity_3_outlined,
          title: 'Beliefs and values',
          subtitle: 'Optional-feeling choices, with privacy built in.',
        ),
        _compactDropdown(
          key: const Key('religionField'),
          label: 'Religious or spiritual outlook',
          value: _religion,
          options: const [
            'Spiritual',
            'Hindu',
            'Muslim',
            'Christian',
            'Buddhist',
            'Agnostic',
            'Atheist',
            'Other',
            'Prefer not to say',
          ],
          onChanged: (value) => setState(() => _religion = value),
        ),
        const SizedBox(height: 12),
        _compactDropdown(
          key: const Key('politicsField'),
          label: 'Political outlook',
          value: _politics,
          options: const [
            'Liberal',
            'Moderate',
            'Conservative',
            'Not political',
            'Other',
            'Prefer not to say',
          ],
          onChanged: (value) => setState(() => _politics = value),
        ),
        _PrivacyToggle(
          value: _showValues,
          onChanged: (value) => setState(() => _showValues = value),
          label: _showValues
              ? 'Values visible on profile'
              : 'Values kept private',
        ),
        const SizedBox(height: 24),
        _SectionLabel(
          icon: Icons.self_improvement_rounded,
          title: 'Lifestyle',
          subtitle: 'Honest answers lead to better compatibility.',
        ),
        _compactDropdown(
          key: const Key('drinkingField'),
          label: 'Drinking',
          value: _drinking,
          options: const [
            'Never',
            'Sometimes',
            'Socially',
            'Regularly',
            'Prefer not to say',
          ],
          onChanged: (value) => setState(() => _drinking = value),
        ),
        const SizedBox(height: 12),
        _compactDropdown(
          key: const Key('smokingField'),
          label: 'Smoking',
          value: _smoking,
          options: const [
            'Never',
            'Sometimes',
            'Socially',
            'Regularly',
            'Prefer not to say',
          ],
          onChanged: (value) => setState(() => _smoking = value),
        ),
        _PrivacyToggle(
          value: _showLifestyle,
          onChanged: (value) => setState(() => _showLifestyle = value),
          label: _showLifestyle
              ? 'Lifestyle visible on profile'
              : 'Lifestyle kept private',
        ),
      ],
    );
  }

  Widget _singleChoice({
    required String keyPrefix,
    required List<String> options,
    required String? value,
    required ValueChanged<String> onChanged,
  }) {
    return Column(
      children: options.map((option) {
        final selected = value == option;
        return Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: _ChoiceCard(
            key: Key('$keyPrefix$option'),
            label: option,
            selected: selected,
            onTap: () => onChanged(option),
          ),
        );
      }).toList(),
    );
  }

  Widget _multiChoice({
    required String keyPrefix,
    required List<String> options,
    required Set<String> selected,
  }) {
    return Column(
      children: options.map((option) {
        final isSelected = selected.contains(option);
        return Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: _ChoiceCard(
            key: Key('$keyPrefix$option'),
            label: option,
            selected: isSelected,
            multi: true,
            onTap: () => setState(
              () => isSelected ? selected.remove(option) : selected.add(option),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _compactDropdown({
    required Key key,
    required String label,
    required String? value,
    required List<String> options,
    required ValueChanged<String?> onChanged,
  }) {
    return DropdownButtonFormField<String>(
      key: key,
      initialValue: value,
      decoration: InputDecoration(labelText: label),
      items: options
          .map((option) => DropdownMenuItem(value: option, child: Text(option)))
          .toList(),
      onChanged: onChanged,
    );
  }

  Widget _photosStep() {
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: 14,
        mainAxisSpacing: 14,
        childAspectRatio: 0.82,
      ),
      itemCount: 4,
      itemBuilder: (context, index) {
        return InkWell(
          onTap: () => ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Photo picker will be connected later.'),
            ),
          ),
          borderRadius: BorderRadius.circular(22),
          child: Ink(
            decoration: BoxDecoration(
              color: index == 0 ? BondCircleColors.lavender : Colors.white,
              borderRadius: BorderRadius.circular(22),
              border: Border.all(color: BondCircleColors.border),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  index == 0 ? Icons.add_a_photo_outlined : Icons.add_rounded,
                  color: BondCircleColors.purple,
                  size: 30,
                ),
                const SizedBox(height: 10),
                Text(index == 0 ? 'Main photo' : 'Add photo'),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFFFFE3EB), BondCircleColors.lavender],
            ),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Icon(icon, color: BondCircleColors.purple),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                subtitle,
                style: const TextStyle(color: BondCircleColors.muted),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _ChoiceCard extends StatelessWidget {
  const _ChoiceCard({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
    this.multi = false,
  });

  final String label;
  final bool selected;
  final bool multi;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: selected ? const Color(0xFFFFEDF2) : Colors.white,
    borderRadius: BorderRadius.circular(20),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 170),
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 17),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: selected
                ? BondCircleColors.primary
                : BondCircleColors.border,
            width: selected ? 1.6 : 1,
          ),
          boxShadow: selected
              ? null
              : const [
                  BoxShadow(
                    color: Color(0x0D231C24),
                    blurRadius: 18,
                    offset: Offset(0, 7),
                  ),
                ],
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                label,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
            Icon(
              selected
                  ? (multi
                        ? Icons.check_box_rounded
                        : Icons.radio_button_checked_rounded)
                  : (multi
                        ? Icons.check_box_outline_blank_rounded
                        : Icons.radio_button_off_rounded),
              color: selected
                  ? BondCircleColors.primary
                  : BondCircleColors.muted,
            ),
          ],
        ),
      ),
    ),
  );
}

class _PrivacyToggle extends StatelessWidget {
  const _PrivacyToggle({
    required this.value,
    required this.onChanged,
    required this.label,
  });
  final bool value;
  final ValueChanged<bool> onChanged;
  final String label;

  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.only(top: 4),
    child: Material(
      color: BondCircleColors.lavender.withValues(alpha: .55),
      borderRadius: BorderRadius.circular(18),
      child: SwitchListTile(
        value: value,
        onChanged: onChanged,
        secondary: Icon(
          value ? Icons.visibility_outlined : Icons.visibility_off_outlined,
        ),
        title: Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: const Text('You can update this anytime.'),
      ),
    ),
  );
}

class ProfilePreviewScreen extends StatefulWidget {
  const ProfilePreviewScreen({
    super.key,
    required this.name,
    required this.age,
    required this.city,
    required this.bio,
    required this.interests,
    required this.gender,
    required this.datingIntention,
    required this.relationshipStyle,
    required this.datingPreferences,
    this.orientation = '',
    this.profileApiService,
  });

  final String name;
  final String age;
  final String city;
  final String bio;
  final List<String> interests;
  final String gender;
  final String datingIntention;
  final String relationshipStyle;
  final List<String> datingPreferences;
  final String orientation;
  final ProfileApiService? profileApiService;

  @override
  State<ProfilePreviewScreen> createState() => _ProfilePreviewScreenState();
}

class _ProfilePreviewScreenState extends State<ProfilePreviewScreen> {
  bool _isSaving = false;

  Future<void> _handleSave() async {
    if (_isSaving) return;
    setState(() => _isSaving = true);

    try {
      if (AuthSession.instance.isAuthenticated &&
          widget.gender.isNotEmpty &&
          widget.datingIntention.isNotEmpty &&
          widget.relationshipStyle.isNotEmpty &&
          widget.interests.isNotEmpty) {
        final service = widget.profileApiService ?? ProfileApiService();
        await service.saveProfile(
          ProfileData(
            gender: widget.gender,
            orientation: widget.orientation,
            connectionIntention: widget.datingIntention,
            relationshipStyle: widget.relationshipStyle,
            interests: widget.interests,
          ),
        );
      }
    } finally {
      if (mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute<void>(
            builder: (_) => InterestCirclesScreen(displayName: widget.name),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Profile preview')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Container(
            height: 300,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [BondCircleColors.lavender, Color(0xFFFFDFE8)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(30),
            ),
            child: const Center(
              child: Icon(Icons.person_rounded, size: 110, color: Colors.white),
            ),
          ),
          const SizedBox(height: 24),
          Text('${widget.name}, ${widget.age}', style: Theme.of(context).textTheme.displaySmall),
          const SizedBox(height: 6),
          Row(
            children: [
              const Icon(Icons.location_on_outlined, size: 19),
              const SizedBox(width: 5),
              Text(widget.city),
            ],
          ),
          const SizedBox(height: 20),
          Text(widget.bio, style: Theme.of(context).textTheme.bodyLarge),
          const SizedBox(height: 24),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: widget.interests.map((item) => Chip(label: Text(item))).toList(),
          ),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(22),
              border: Border.all(color: BondCircleColors.border),
            ),
            child: Column(
              children: [
                _PreviewDetail(Icons.badge_outlined, 'Identity', widget.gender),
                _PreviewDetail(
                  Icons.explore_outlined,
                  'Looking for',
                  widget.datingIntention,
                ),
                _PreviewDetail(
                  Icons.join_inner_rounded,
                  'Relationship style',
                  widget.relationshipStyle,
                ),
                _PreviewDetail(
                  Icons.people_alt_outlined,
                  'Open to',
                  widget.datingPreferences.join(', '),
                  last: true,
                ),
              ],
            ),
          ),
          const SizedBox(height: 28),
          FilledButton(
            key: const Key('saveProfileButton'),
            onPressed: _isSaving ? null : _handleSave,
            child: _isSaving
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Text('Save and choose circles'),
          ),
        ],
      ),
    );
  }
}

class _PreviewDetail extends StatelessWidget {
  const _PreviewDetail(this.icon, this.label, this.value, {this.last = false});
  final IconData icon;
  final String label;
  final String value;
  final bool last;

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(bottom: last ? 0 : 16),
    child: Row(
      children: [
        Icon(icon, color: BondCircleColors.purple),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  color: BondCircleColors.muted,
                  fontSize: 12,
                ),
              ),
              Text(value, style: const TextStyle(fontWeight: FontWeight.w800)),
            ],
          ),
        ),
      ],
    ),
  );
}
